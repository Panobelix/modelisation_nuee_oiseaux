import numpy as np
import matplotlib.pyplot as plt
import matplotlib.animation as animation
from matplotlib.patches import Rectangle, Circle
from scipy.spatial import cKDTree
import argparse
import time

# =====================================================================
# CLASE VIENTO
# =====================================================================
class WindField:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.time = 0.0
        self.strength = 1.2
        self.base_freq = 0.08
        self.freq_var = 0.04
        self.grid_x, self.grid_y = np.meshgrid(
            np.linspace(-width/2, width/2, 25),
            np.linspace(-height/2, height/2, 20)
        )

    def update_time(self, dt):
        self.time += dt

    def get_wind_at_pos(self, positions):
        x = positions[:, 0]
        y = positions[:, 1]
        current_freq = self.base_freq + np.sin(self.time * 0.2) * self.freq_var
        wx = np.cos(y * current_freq + self.time * 0.5)
        wy = np.sin(x * current_freq + self.time * 0.3)
        return np.column_stack((wx, wy)) * self.strength

    def get_visual_grid(self):
        flat_pos = np.column_stack((self.grid_x.ravel(), self.grid_y.ravel()))
        wind = self.get_wind_at_pos(flat_pos)
        return self.grid_x, self.grid_y, wind[:, 0], wind[:, 1]

# =====================================================================
# CLASE DEPREDADOR
# =====================================================================
class Predator:
    def __init__(self, width, height, v_birds):
        self.width = width; self.height = height
        self.state = "HUNTING"; self.birds_eaten = 0
        self.hunger_quota = np.random.randint(1, 6); self.digestion_timer = 0.0
        self.is_roaring = False; self.roar_cooldown = 0.0; self.roar_duration_timer = 0.0
        self.roar_radius = 30.0
        
        # Física Letal
        self.v_turn = v_birds * 3.0      
        self.v_straight = v_birds * 5.0  
        self.max_force = 10.0            
        self.wall_margin = 2.0           
        self.pos = np.array([0.0, 0.0])
        self.vel = np.random.rand(2) - 0.5
        self.vel = (self.vel / np.linalg.norm(self.vel)) * self.v_straight

    def _get_wall_steering(self):
        steer = np.zeros(2); desired = None
        if self.pos[0] > self.width/2 - self.wall_margin: desired = np.array([-self.v_straight, self.vel[1]])
        elif self.pos[0] < -self.width/2 + self.wall_margin: desired = np.array([self.v_straight, self.vel[1]])
        if self.pos[1] > self.height/2 - self.wall_margin: desired = np.array([self.vel[0], -self.v_straight])
        elif self.pos[1] < -self.height/2 + self.wall_margin: desired = np.array([self.vel[0], self.v_straight])
        if desired is not None:
            desired = (desired / np.linalg.norm(desired)) * self.v_straight
            steer = desired - self.vel
            steer = np.clip(steer, -self.max_force * 3, self.max_force * 3)
            return steer
        return np.zeros(2)

    def update(self, birds_pos, dt, wind_force):
        if self.is_roaring:
            self.roar_duration_timer -= dt
            if self.roar_duration_timer <= 0:
                self.is_roaring = False; self.roar_cooldown = np.random.uniform(5.0, 12.0)
        else:
            self.roar_cooldown -= dt
            if self.roar_cooldown <= 0 and self.state == "HUNTING":
                if np.random.rand() < 0.02: self.is_roaring = True; self.roar_duration_timer = 1.5

        if self.state == "DIGESTING":
            self.digestion_timer -= dt
            if self.digestion_timer <= 0:
                self.state = "HUNTING"; self.birds_eaten = 0; self.hunger_quota = np.random.randint(1, 6)

        wall_force = self._get_wall_steering()
        behavior_force = np.zeros(2)
        captured_indices = []

        if self.state == "HUNTING":
            diff = birds_pos - self.pos
            dists_sq = np.sum(diff**2, axis=1) 
            closest_idx = np.argmin(dists_sq)
            target_pos = birds_pos[closest_idx]
            dist_to_target = np.sqrt(dists_sq[closest_idx])
            
            desired = target_pos - self.pos
            desired_mag = np.linalg.norm(desired)
            if desired_mag > 0:
                desired = (desired / desired_mag) * self.v_straight
                steer = desired - self.vel
                steer_mag = np.linalg.norm(steer)
                if steer_mag > self.max_force: steer = (steer / steer_mag) * self.max_force
                behavior_force = steer
            
            # Hitbox 2.0 (Coincide con flecha visual 4.0 centrada)
            if dist_to_target < 2.0: 
                self.birds_eaten += 1
                captured_indices.append(closest_idx)
                if self.birds_eaten >= self.hunger_quota:
                    self.state = "DIGESTING"; self.digestion_timer = np.random.uniform(5, 15) + (self.birds_eaten * 2)

        elif self.state == "DIGESTING":
            speed = np.linalg.norm(self.vel)
            if speed > 0:
                desired_vel = (self.vel / speed) * (self.v_turn * 0.4)
                steering = desired_vel - self.vel
                wander_noise = (np.random.rand(2) - 0.5) * 4.0
                behavior_force = steering + wander_noise

        acceleration = wall_force + behavior_force + (wind_force * 0.3)
        if np.linalg.norm(wall_force) > 0.1: acceleration = wall_force + behavior_force * 0.5
        self.vel += acceleration * dt
        
        speed = np.linalg.norm(self.vel)
        max_speed = self.v_straight if self.state == "HUNTING" else self.v_turn
        if speed > max_speed: self.vel = (self.vel / speed) * max_speed
        self.pos += self.vel * dt
        np.clip(self.pos[0], -self.width/2, self.width/2, out=self.pos[0:1])
        np.clip(self.pos[1], -self.height/2, self.height/2, out=self.pos[1:2])
        return captured_indices

# =====================================================================
# CLASE COMIDA
# =====================================================================
class FoodResource:
    def __init__(self, width, height, safe_margin):
        self.width = width; self.height = height; self.safe_margin = safe_margin
        self.pos = np.array([0.0, 0.0])
        self.respawn()
    def respawn(self):
        safe_w = (self.width/2) - self.safe_margin - 5.0
        safe_h = (self.height/2) - self.safe_margin - 5.0
        self.pos = np.array([np.random.uniform(-safe_w, safe_w), np.random.uniform(-safe_h, safe_h)])

# =====================================================================
# CLASE FLOCK (ECOSISTEMA)
# =====================================================================
class OrganicFlock:
    def __init__(self, N=300, width=80, height=60, params=None):
        self.N = N; self.width = width; self.height = height
        p = params if params else {}
        self.v0 = p.get('v0', 3.5)
        self.R_per = p.get('R_per', 5.0); self.R_sep = p.get('R_sep', 1.2); self.fov_deg = p.get('fov', 280)
        self.k_align = 1.0; self.k_coh = 0.8; self.k_sep = 2.5
        self.k_wander = 0.6; self.wander_rate = 0.3; self.wander_theta = np.random.rand(N) * 2 * np.pi
        
        self.speed_follower = 1.00; self.speed_leader = 0.80; self.speed_tired = 0.60; self.speed_relax = 0.05
        self.stamina = np.ones(N) * 100.0; self.is_leader = np.zeros(N, dtype=bool)
        self.is_tired = np.zeros(N, dtype=bool); self.is_alerted = np.zeros(N, dtype=bool)
        self.fatigue_rate = 0.6; self.recovery_rate = 0.4      

        self.dist_zone1 = 15.0; self.dist_zone2 = 8.0; self.dist_zone3 = 3.0   
        self.k_center = 2.0; self.k_wall_base = 15.0
        self.dt = 0.05; self.fov_threshold = np.cos(np.deg2rad(self.fov_deg / 2.0))

        safe_w = width - 2 * self.dist_zone1; safe_h = height - 2 * self.dist_zone1
        self.pos = (np.random.rand(N, 2) - 0.5) * np.array([safe_w, safe_h])
        angles = np.random.rand(N) * 2 * np.pi
        self.vel = np.column_stack((np.cos(angles), np.sin(angles))) * self.v0
        
        self.predator = Predator(width, height, self.v0)
        self.pred_visual_radius = 6.0; self.k_predator = 100.0
        
        self.food = FoodResource(width, height, safe_margin=self.dist_zone1)
        self.scent_radius = 35.0; self.k_food = 3.0
        self.wind = WindField(width, height)

    def step(self):
        self.wind.update_time(self.dt)
        wind_at_pred = self.wind.get_wind_at_pos(np.array([self.predator.pos]))[0]

        captured = self.predator.update(self.pos, self.dt, wind_at_pred)
        if captured:
            for idx in captured:
                side = np.random.randint(0, 4)
                if side == 0: self.pos[idx] = [-self.width/2, np.random.uniform(-self.height/2, self.height/2)]
                elif side == 1: self.pos[idx] = [self.width/2, np.random.uniform(-self.height/2, self.height/2)]
                elif side == 2: self.pos[idx] = [np.random.uniform(-self.width/2, self.width/2), -self.height/2]
                elif side == 3: self.pos[idx] = [np.random.uniform(-self.width/2, self.width/2), self.height/2]
                self.vel[idx] *= -1; self.stamina[idx] = 100; self.is_alerted[idx] = False

        to_pred = self.predator.pos - self.pos
        dist_sq_pred = np.sum(to_pred**2, axis=1)
        visual_alert = dist_sq_pred < self.pred_visual_radius**2 
        audio_alert = np.zeros(self.N, dtype=bool)
        if self.predator.is_roaring: audio_alert = dist_sq_pred < self.predator.roar_radius**2
        self.is_alerted = visual_alert | audio_alert

        tree = cKDTree(self.pos)
        pairs = tree.query_pairs(self.R_per, output_type='ndarray')
        F_align = np.zeros((self.N, 2)); F_coh = np.zeros((self.N, 2)); F_sep = np.zeros((self.N, 2))
        counts = np.zeros(self.N)
        
        if len(pairs) > 0:
            i = pairs[:, 0]; j = pairs[:, 1]
            idx_i = np.concatenate([i, j]); idx_j = np.concatenate([j, i])
            vec_ij = self.pos[idx_j] - self.pos[idx_i]
            dist_ij = np.linalg.norm(vec_ij, axis=1); dist_safe = np.maximum(dist_ij, 1e-5)
            vec_ij_unit = vec_ij / dist_safe[:, None]
            vel_norm = np.linalg.norm(self.vel, axis=1); vel_norm = np.maximum(vel_norm, 1e-5)
            vel_unit = self.vel / vel_norm[:, None]
            dot_prod = np.sum(vel_unit[idx_i] * vec_ij_unit, axis=1)
            visible_mask = dot_prod > self.fov_threshold
            v_i = idx_i[visible_mask]; v_j = idx_j[visible_mask]
            v_vec_ij = vec_ij[visible_mask]; v_dist = dist_ij[visible_mask]
            
            if len(v_i) > 0:
                np.add.at(counts, v_i, 1)
                np.add.at(F_align, v_i, self.vel[v_j])
                np.add.at(F_coh, v_i, self.pos[v_j])
                sep_mask = v_dist < self.R_sep
                if np.any(sep_mask):
                    s_i = v_i[sep_mask]; s_vec = -v_vec_ij[sep_mask]; s_dist = np.maximum(v_dist[sep_mask], 0.1)
                    s_force = s_vec / (s_dist[:, None]**2)
                    np.add.at(F_sep, s_i, s_force)
                alert_influence = np.zeros(self.N)
                np.add.at(alert_influence, v_i, self.is_alerted[v_j].astype(float))
                self.is_alerted = self.is_alerted | (alert_influence > 0)

        current_leaders = np.ones(self.N, dtype=bool)
        if len(pairs) > 0:
            front_mask = (dot_prod > 0.3) & (dist_ij < self.R_per)
            has_front = np.zeros(self.N, dtype=bool)
            has_front[idx_i[front_mask]] = True 
            current_leaders = ~has_front

        mask_has_neighbors = counts > 0
        counts_safe = np.maximum(counts, 1)[:, None]
        F_align[mask_has_neighbors] = (F_align[mask_has_neighbors] / counts_safe[mask_has_neighbors]) - self.vel[mask_has_neighbors]
        F_coh[mask_has_neighbors] = (F_coh[mask_has_neighbors] / counts_safe[mask_has_neighbors]) - self.pos[mask_has_neighbors]
        F_align *= self.k_align; F_coh *= self.k_coh; F_coh[counts > 12] *= 0.1
        mag_sep = np.linalg.norm(F_sep, axis=1, keepdims=True); limit_sep = 12.0
        mask_limit = (mag_sep > limit_sep).flatten()
        F_sep[mask_limit] = (F_sep[mask_limit] / mag_sep[mask_limit]) * limit_sep
        F_sep *= self.k_sep

        self.is_leader = current_leaders
        spending = self.is_leader & (~self.is_tired)
        self.stamina[spending] -= self.fatigue_rate
        recovering = ~self.is_leader
        self.stamina[recovering] += self.recovery_rate
        self.stamina = np.clip(self.stamina, 0, 100)
        self.is_tired[self.stamina <= 0] = True; self.is_tired[self.stamina >= 100] = False

        random_displacement = (np.random.rand(self.N) - 0.5) * self.wander_rate
        self.wander_theta += random_displacement
        wander_vec = np.column_stack((np.cos(self.wander_theta), np.sin(self.wander_theta)))
        F_wander = wander_vec * self.k_wander

        # Comida
        F_food = np.zeros((self.N, 2))
        to_food = self.food.pos - self.pos; dist_food = np.linalg.norm(to_food, axis=1)
        if np.any(dist_food < 2.0): self.food.respawn() 
        smell_mask = (dist_food < self.scent_radius)
        if np.any(smell_mask):
            dists = dist_food[smell_mask]; dirs = to_food[smell_mask]
            intensity = self.k_food * (1.0 - (dists / self.scent_radius)); intensity = np.maximum(intensity, 0)
            F_food[smell_mask] = (dirs / np.maximum(dists, 1.0)[:, None]) * intensity[:, None]

        # Huida
        F_predator = np.zeros((self.N, 2))
        if np.any(self.is_alerted):
            flee_dirs = -(self.predator.pos - self.pos[self.is_alerted])
            dists_p = np.linalg.norm(flee_dirs, axis=1)
            F_predator[self.is_alerted] = (flee_dirs / np.maximum(dists_p, 0.1)[:, None]) * (self.k_predator / np.maximum(dists_p, 0.1)[:, None])

        F_wind = self.wind.get_wind_at_pos(self.pos)

        F_wall = np.zeros((self.N, 2)); social_weight = np.ones((self.N, 1))
        d_r = (self.width / 2) - self.pos[:, 0]; d_l = self.pos[:, 0] - (-self.width / 2)
        d_t = (self.height / 2) - self.pos[:, 1]; d_b = self.pos[:, 1] - (-self.height / 2)
        def apply_wall_vec(dists, axis, direction):
            mask = dists < self.dist_zone1
            if not np.any(mask): return
            d = dists[mask]; force = np.zeros_like(d); w = social_weight[mask, 0]
            z3 = d < self.dist_zone3
            if np.any(z3): force[z3] = 20.0 * ((self.dist_zone3 - d[z3] + 1.0) / 2.0)**2; w[z3] = 0.0
            z2 = (d >= self.dist_zone3) & (d < self.dist_zone2)
            if np.any(z2): force[z2] = 3.0; w[z2] *= 0.8
            z1 = (d >= self.dist_zone2)
            if np.any(z1): force[z1] = 0.5
            if axis == 0: F_wall[mask, 0] += direction * force
            else:         F_wall[mask, 1] += direction * force
            social_weight[mask, 0] = w
        apply_wall_vec(d_r, 0, -1); apply_wall_vec(d_l, 0, 1); apply_wall_vec(d_t, 1, -1); apply_wall_vec(d_b, 1, 1)
        mask_any_wall = (d_r < self.dist_zone1) | (d_l < self.dist_zone1) | (d_t < self.dist_zone1) | (d_b < self.dist_zone1)
        if np.any(mask_any_wall):
            to_center = -self.pos[mask_any_wall]
            dist_c = np.linalg.norm(to_center, axis=1, keepdims=True)
            F_wall[mask_any_wall] += (to_center / (dist_c + 0.1)) * self.k_center

        accel = ((self.k_align * F_align + 
                  self.k_coh * F_coh + 
                  self.k_sep * F_sep + 
                  F_wander + F_food + F_wind) * social_weight) + F_wall + F_predator
        self.vel += accel * self.dt

        # --- GESTIÓN DE VELOCIDAD POR ESTADOS (CORREGIDO) ---
        current_speeds = np.linalg.norm(self.vel, axis=1, keepdims=True)
        current_speeds[current_speeds < 1e-5] = 1.0
        
        # 1. Base (Seguidor Fresco)
        target_speeds = np.ones((self.N, 1)) * self.v0
        
        # 2. Máscaras
        mask_alert = self.is_alerted
        mask_tired = self.is_tired
        mask_leader = self.is_leader & (~mask_tired) # Líder solo si no está cansado
        
        # 3. Lógica Prioritaria
        # A: Pánico Fresco -> Sprint (1.5x)
        target_speeds[mask_alert & (~mask_tired)] = self.v0 * 1.5 
        
        # B: Pánico Cansado -> Velocidad Base (1.0x) - No pueden sprintar
        target_speeds[mask_alert & mask_tired] = self.v0 * 1.0 
        
        # C: Normal (No pánico)
        mask_normal = ~mask_alert
        target_speeds[mask_normal & mask_leader] *= self.speed_leader # 0.8x
        target_speeds[mask_normal & mask_tired] *= self.speed_tired   # 0.6x
        
        new_speeds = current_speeds + (target_speeds - current_speeds) * self.speed_relax
        self.vel = (self.vel / current_speeds) * new_speeds
        self.pos += self.vel * self.dt

        crashed_r = self.pos[:, 0] > self.width/2; crashed_l = self.pos[:, 0] < -self.width/2
        crashed_t = self.pos[:, 1] > self.height/2; crashed_b = self.pos[:, 1] < -self.height/2
        crashed_any = crashed_r | crashed_l | crashed_t | crashed_b
        if np.any(crashed_any):
            self.stamina[crashed_any] = 0; self.is_tired[crashed_any] = True
            if np.any(crashed_r): self.vel[crashed_r, 0] *= -0.5; self.pos[crashed_r, 0] = self.width/2 - 0.1
            if np.any(crashed_l): self.vel[crashed_l, 0] *= -0.5; self.pos[crashed_l, 0] = -self.width/2 + 0.1
            if np.any(crashed_t): self.vel[crashed_t, 1] *= -0.5; self.pos[crashed_t, 1] = self.height/2 - 0.1
            if np.any(crashed_b): self.vel[crashed_b, 1] *= -0.5; self.pos[crashed_b, 1] = -self.height/2 + 0.1

# =====================================================================
# VISUALIZACIÓN
# =====================================================================
def run_simulation(show_arrows=False):
    N_BIRDS = 500; WIDTH, HEIGHT = 160, 100
    sim = OrganicFlock(N=N_BIRDS, width=WIDTH, height=HEIGHT)
    fig, ax = plt.subplots(figsize=(16, 10), facecolor='#101010') 
    ax.set_facecolor('#050505')
    ax.set_xlim(-WIDTH/2 - 2, WIDTH/2 + 2); ax.set_ylim(-HEIGHT/2 - 2, HEIGHT/2 + 2)
    ax.set_aspect('equal'); ax.set_xticks([]); ax.set_yticks([])

    X, Y, U, V = sim.wind.get_visual_grid()
    wind_quiver = ax.quiver(X, Y, U, V, color='white', alpha=0.15, scale=40, width=0.002, zorder=11)

    inset_z1 = sim.dist_zone1
    ax.add_patch(Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FFFF00', alpha=0.08, zorder=1))
    m_safe = sim.dist_zone1
    ax.add_patch(Rectangle((-WIDTH/2 + m_safe, -HEIGHT/2 + m_safe), WIDTH-2*m_safe, HEIGHT-2*m_safe, color='#050505', zorder=10))
    ax.add_patch(Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FF0000', alpha=0.2, zorder=1))
    m3 = sim.dist_zone3
    ax.add_patch(Rectangle((-WIDTH/2 + m3, -HEIGHT/2 + m3), WIDTH - 2*m3, HEIGHT - 2*m3, color='#FF8800', alpha=0.2, zorder=2))
    m2 = sim.dist_zone2
    ax.add_patch(Rectangle((-WIDTH/2 + m2, -HEIGHT/2 + m2), WIDTH - 2*m2, HEIGHT - 2*m2, color='#FFFF00', alpha=0.1, zorder=3))
    ax.add_patch(Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, linewidth=3, edgecolor='#888888', facecolor='none', zorder=5))

    roar_circle = Circle((0,0), radius=1, color='white', fill=False, linewidth=2, alpha=0.0, zorder=25)
    ax.add_patch(roar_circle)
    scent_circle = Circle((0,0), radius=sim.scent_radius, color='#00FF00', fill=True, alpha=0.08, zorder=12)
    ax.add_patch(scent_circle)
    food_marker, = ax.plot([], [], marker='*', markersize=18, color='gold', zorder=25, linestyle='None')

    title_text = "SIMULACIÓN FINAL"
    title_obj = ax.text(0.02, 0.98, title_text, transform=ax.transAxes, color="gray", fontsize=11, va='top', family='monospace')

    birds = ax.quiver(sim.pos[:,0], sim.pos[:,1], sim.vel[:,0], sim.vel[:,1],
                      color='cyan', scale=None, scale_units='xy', angles='xy',
                      headlength=4.5, headaxislength=3.5, headwidth=3.5,
                      width=0.003, minshaft=0, pivot='mid', zorder=20)

    pred_marker = ax.quiver(sim.predator.pos[0], sim.predator.pos[1], 
                            sim.predator.vel[0], sim.predator.vel[1],
                            color='#FF5500', 
                            scale=1.0, scale_units='xy', angles='xy', 
                            headlength=1.0, headaxislength=0.8, headwidth=0.8, 
                            width=0.015, minshaft=0, pivot='mid', zorder=30)

    vectors = None
    if show_arrows:
        vectors = ax.quiver(sim.pos[:,0], sim.pos[:,1], sim.vel[:,0], sim.vel[:,1],
                            color='white', alpha=0.4, width=0.0015, scale=25, headlength=0, headaxislength=0, zorder=15)

    def update(frame):
        sim.step()
        birds.set_offsets(sim.pos)
        vis_vel = sim.vel / np.linalg.norm(sim.vel, axis=1, keepdims=True)
        birds.set_UVC(vis_vel[:,0], vis_vel[:,1])
        
        # --- COLORES FINALES ---
        colors = np.array([[0, 1, 1, 0.8]] * sim.N) 
        mask_alert = sim.is_alerted
        mask_tired = sim.is_tired
        mask_leader = sim.is_leader
        
        colors[mask_leader & (~mask_tired) & (~mask_alert)] = [1, 0.3, 0, 1] # Rojo
        colors[mask_tired & (~mask_alert)] = [0.5, 0.5, 0.5, 0.6] # Gris
        colors[mask_alert & (~mask_tired)] = [1, 0.9, 0.0, 1.0] # Amarillo
        colors[mask_alert & mask_tired] = [0.82, 0.41, 0.12, 1.0] # Naranja Oscuro (Pánico Cansado)

        birds.set_facecolors(colors)
        
        food_marker.set_data([sim.food.pos[0]], [sim.food.pos[1]])
        scent_circle.center = sim.food.pos
        _, _, U, V = sim.wind.get_visual_grid()
        wind_quiver.set_UVC(U, V)

        pred_marker.set_offsets(sim.predator.pos)
        p_vel_norm = np.linalg.norm(sim.predator.vel)
        if p_vel_norm > 0:
            p_vel_vis = (sim.predator.vel / p_vel_norm) * 4.0 
            pred_marker.set_UVC(p_vel_vis[0], p_vel_vis[1])
        
        if sim.predator.state == "HUNTING":
            if sim.predator.is_roaring:
                roar_circle.center = sim.predator.pos; roar_circle.set_visible(True)
                wave_radius = 5.0 + (frame % 10) * 3.0 
                roar_circle.set_radius(wave_radius); roar_circle.set_alpha(0.5 - (frame % 10) * 0.04)
                pred_marker.set_color('#FFFF00') 
            else:
                roar_circle.set_visible(False); pred_marker.set_color('#FF4400') 
            title_obj.set_text(f"CAZANDO ({sim.predator.birds_eaten}/{sim.predator.hunger_quota})")
        elif sim.predator.state == "STUNNED":
            roar_circle.set_visible(False); pred_marker.set_color('#555555'); title_obj.set_text("¡ATURDIDO!")
        else:
            roar_circle.set_visible(False); pred_marker.set_color('#8844AA'); title_obj.set_text("DIGIRIENDO")

        if show_arrows:
            vectors.set_offsets(sim.pos); vectors.set_UVC(sim.vel[:,0], sim.vel[:,1])
            return birds, pred_marker, vectors, title_obj, roar_circle, food_marker, scent_circle, wind_quiver
        return birds, pred_marker, title_obj, roar_circle, food_marker, scent_circle, wind_quiver

    ani = animation.FuncAnimation(fig, update, frames=None, interval=16, blit=False, cache_frame_data=False)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--arrows", action="store_true", help="Mostrar vectores de dirección")
    args = parser.parse_args()
    run_simulation(show_arrows=args.arrows)