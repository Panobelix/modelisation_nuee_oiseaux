import numpy as np
import matplotlib.pyplot as plt
import matplotlib.animation as animation
from matplotlib.patches import Rectangle
from scipy.spatial import cKDTree
import argparse

# =====================================================================
# CLASE DE SIMULACIÓN: PENETRACIÓN DE ZONAS PERMITIDA
# =====================================================================

class OrganicFlock:
    def __init__(self, N=300, width=80, height=60, params=None):
        self.N = N
        self.width = width
        self.height = height
        
        p = params if params else {}
        self.v0 = p.get('v0', 3.5)
        self.R_per = p.get('R_per', 5.0)
        self.R_sep = p.get('R_sep', 1.2)
        self.fov_deg = p.get('fov', 280)
        
        self.k_align = 1.0
        self.k_coh = 0.8
        self.k_sep = 2.5
        self.k_wander = 0.6 
        self.wander_rate = 0.3 
        self.wander_theta = np.random.rand(N) * 2 * np.pi

        self.speed_follower = 1.00
        self.speed_leader   = 0.80
        self.speed_tired    = 0.60
        self.speed_relax    = 0.05

        self.stamina = np.ones(N) * 100.0
        self.is_leader = np.zeros(N, dtype=bool)
        self.is_tired = np.zeros(N, dtype=bool)
        self.fatigue_rate = 0.6       
        self.recovery_rate = 0.4      

        # --- PARÁMETROS DE PAREDES (PERMISIBLES) ---
        self.dist_zone1 = 15.0  # Amarilla (Lejos)
        self.dist_zone2 = 8.0   # Naranja (Media)
        self.dist_zone3 = 3.0   # Roja (Cerca)
        
        self.k_center = 2.0     # Atracción suave al centro

        self.dt = 0.05
        self.fov_threshold = np.cos(np.deg2rad(self.fov_deg / 2.0))

        safe_w = width - 2 * self.dist_zone1
        safe_h = height - 2 * self.dist_zone1
        self.pos = (np.random.rand(N, 2) - 0.5) * np.array([safe_w, safe_h])
        
        angles = np.random.rand(N) * 2 * np.pi
        self.vel = np.column_stack((np.cos(angles), np.sin(angles))) * self.v0

    def step(self):
        tree = cKDTree(self.pos)
        neighbors_list = tree.query_ball_point(self.pos, self.R_per)

        vel_norm = np.linalg.norm(self.vel, axis=1)
        vel_norm[vel_norm == 0] = 1.0
        vel_unit = self.vel / vel_norm[:, None]

        F_align = np.zeros((self.N, 2))
        F_coh = np.zeros((self.N, 2))
        F_sep = np.zeros((self.N, 2))
        
        current_leaders = np.ones(self.N, dtype=bool)

        for i, neighbors in enumerate(neighbors_list):
            if len(neighbors) <= 1: continue
            nb_idx = np.array(neighbors)
            nb_idx = nb_idx[nb_idx != i]
            if len(nb_idx) == 0: continue

            vec_to_nb = self.pos[nb_idx] - self.pos[i]
            dist_nb = np.linalg.norm(vec_to_nb, axis=1)
            
            with np.errstate(divide='ignore', invalid='ignore'):
                vec_to_nb_unit = vec_to_nb / dist_nb[:, None]
            
            dot = np.sum(vel_unit[i] * vec_to_nb_unit, axis=1)
            visible_mask = dot > self.fov_threshold
            
            front_mask = (dot > 0.3) & (dist_nb < self.R_per)
            if np.any(front_mask):
                current_leaders[i] = False
            
            valid_idx = nb_idx[visible_mask]
            
            density = len(valid_idx)
            k_c_local = self.k_coh
            if density > 12: k_c_local *= 0.1 
            
            if density > 0:
                avg_vel = np.mean(self.vel[valid_idx], axis=0)
                F_align[i] = self.k_align * (avg_vel - self.vel[i])

                avg_pos = np.mean(self.pos[valid_idx], axis=0)
                F_coh[i] = k_c_local * (avg_pos - self.pos[i])

                valid_dist = dist_nb[visible_mask]
                sep_mask = valid_dist < self.R_sep
                if np.any(sep_mask):
                    rep_vecs = -vec_to_nb[visible_mask][sep_mask]
                    rep_dists = np.maximum(valid_dist[sep_mask], 0.1)
                    force_val = rep_vecs / (rep_dists[:, None]**2)
                    F_sep[i] = np.sum(force_val, axis=0)
                    F_sep[i] = np.clip(F_sep[i], -12.0, 12.0)

        self.is_leader = current_leaders
        spending = self.is_leader & (~self.is_tired)
        self.stamina[spending] -= self.fatigue_rate
        recovering = ~self.is_leader
        self.stamina[recovering] += self.recovery_rate
        self.stamina = np.clip(self.stamina, 0, 100)
        self.is_tired[self.stamina <= 0] = True
        self.is_tired[self.stamina >= 100] = False

        random_displacement = (np.random.rand(self.N) - 0.5) * self.wander_rate
        self.wander_theta += random_displacement
        wander_vec = np.column_stack((np.cos(self.wander_theta), np.sin(self.wander_theta)))
        F_wander = wander_vec * self.k_wander

        # --- FÍSICA DE PAREDES CORREGIDA (Permisiva) ---
        F_wall = np.zeros((self.N, 2))
        social_weight = np.ones((self.N, 1))

        d_r = (self.width / 2) - self.pos[:, 0]
        d_l = self.pos[:, 0] - (-self.width / 2)
        d_t = (self.height / 2) - self.pos[:, 1]
        d_b = self.pos[:, 1] - (-self.height / 2)

        # Máscara global de zona (dentro de los 15m)
        mask_any_zone = (d_r < self.dist_zone1) | (d_l < self.dist_zone1) | \
                        (d_t < self.dist_zone1) | (d_b < self.dist_zone1)

        def apply_layered_wall(dists, axis, direction):
            # Solo procesamos a los que están dentro del alcance máximo
            mask = dists < self.dist_zone1
            if not np.any(mask): return

            d = dists[mask]
            force_mag = np.zeros_like(d)
            
            # --- ZONA 3 (ROJA - CRÍTICA: 0m a 3m) ---
            # Fuerza fuerte para evitar el choque inminente
            z3 = d < self.dist_zone3
            if np.any(z3):
                # Fuerza exponencial: empieza en 20 y sube rápido
                # Esto permite entrar un poco en lo rojo antes de ser expulsado
                force_mag[z3] = 20.0 * ((self.dist_zone3 - d[z3] + 1.0) / 2.0)**2
                social_weight[mask][z3] = 0.0 # Pánico

            # --- ZONA 2 (NARANJA - MEDIA: 3m a 8m) ---
            z2 = (d >= self.dist_zone3) & (d < self.dist_zone2)
            if np.any(z2):
                # Fuerza constante y baja (3.0). Se nota pero se puede vencer.
                force_mag[z2] = 3.0
                social_weight[mask][z2] *= 0.8 # Baja influencia social

            # --- ZONA 1 (AMARILLA - SUAVE: 8m a 15m) ---
            z1 = (d >= self.dist_zone2)
            if np.any(z1):
                # Fuerza insignificante (0.5). Solo una brisa.
                force_mag[z1] = 0.5
                # Social intacto (1.0)

            # Aplicar
            if axis == 0: F_wall[mask, 0] += direction * force_mag
            else:         F_wall[mask, 1] += direction * force_mag

        apply_layered_wall(d_r, 0, -1)
        apply_layered_wall(d_l, 0, 1)
        apply_layered_wall(d_t, 1, -1)
        apply_layered_wall(d_b, 1, 1)

        if np.any(mask_any_zone):
            to_center = -self.pos[mask_any_zone]
            dist_c = np.linalg.norm(to_center, axis=1, keepdims=True)
            F_wall[mask_any_zone] += (to_center / (dist_c + 0.1)) * self.k_center

        accel = ((self.k_align * F_align + 
                  self.k_coh * F_coh + 
                  self.k_sep * F_sep + 
                  F_wander) * social_weight) + F_wall

        self.vel += accel * self.dt

        # Velocidades
        current_speeds = np.linalg.norm(self.vel, axis=1, keepdims=True)
        current_speeds[current_speeds < 1e-5] = 1.0
        target_speeds = np.ones((self.N, 1)) * self.v0
        
        active_leader = self.is_leader & (~self.is_tired)
        target_speeds[active_leader] *= self.speed_leader
        target_speeds[self.is_tired] *= self.speed_tired
        
        new_speeds = current_speeds + (target_speeds - current_speeds) * self.speed_relax
        self.vel = (self.vel / current_speeds) * new_speeds
        self.pos += self.vel * self.dt

        # --- CHOQUE FÍSICO ---
        crashed_r = self.pos[:, 0] > self.width/2
        crashed_l = self.pos[:, 0] < -self.width/2
        crashed_t = self.pos[:, 1] > self.height/2
        crashed_b = self.pos[:, 1] < -self.height/2
        crashed_any = crashed_r | crashed_l | crashed_t | crashed_b
        
        if np.any(crashed_any):
            self.stamina[crashed_any] = 0
            self.is_tired[crashed_any] = True
            
            if np.any(crashed_r): 
                self.vel[crashed_r, 0] *= -0.5
                self.pos[crashed_r, 0] = self.width/2 - 0.1
            if np.any(crashed_l): 
                self.vel[crashed_l, 0] *= -0.5
                self.pos[crashed_l, 0] = -self.width/2 + 0.1
            if np.any(crashed_t): 
                self.vel[crashed_t, 1] *= -0.5
                self.pos[crashed_t, 1] = self.height/2 - 0.1
            if np.any(crashed_b): 
                self.vel[crashed_b, 1] *= -0.5
                self.pos[crashed_b, 1] = -self.height/2 + 0.1

# =====================================================================
# VISUALIZACIÓN
# =====================================================================

def generate_random_params():
    return {'v0': 3.5, 'R_per': 5.5, 'R_sep': 1.1, 'fov': 280}

def run_simulation(show_arrows=False):
    N_BIRDS = 350
    WIDTH, HEIGHT = 100, 70
    
    params = generate_random_params()
    sim = OrganicFlock(N=N_BIRDS, width=WIDTH, height=HEIGHT, params=params)

    fig, ax = plt.subplots(figsize=(14, 10), facecolor='#101010') 
    ax.set_facecolor('#050505')
    
    margin_vis = 2
    ax.set_xlim(-WIDTH/2 - margin_vis, WIDTH/2 + margin_vis)
    ax.set_ylim(-HEIGHT/2 - margin_vis, HEIGHT/2 + margin_vis)
    ax.set_aspect('equal')
    ax.set_xticks([])
    ax.set_yticks([])
    
    # --- DIBUJO CORRECTO DE ZONAS (Orden Inverso) ---
    
    # 1. Fondo Amarillo (Zona 1) - El más grande, al fondo
    inset_z1 = sim.dist_zone1
    rect_yel = Rectangle((-WIDTH/2 + inset_z1, -HEIGHT/2 + inset_z1), 
                         WIDTH - 2*inset_z1, HEIGHT - 2*inset_z1, 
                         color='#FFFF00', alpha=0.0, zorder=0) # Invisible en el centro
    # Para dibujar SOLO el marco amarillo:
    # Dibujamos un rectángulo amarillo grande que cubre todo menos el marco físico
    frame_yel = Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FFFF00', alpha=0.08, zorder=1)
    ax.add_patch(frame_yel)
    
    # 2. Fondo Naranja (Zona 2) - Encima del amarillo
    # Tapamos la parte interior del amarillo con el naranja donde empieza zona 2
    inset_z2 = sim.dist_zone2 # 8.0
    frame_org = Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FF8800', alpha=0.0, zorder=2) 
    # Mejor: Usar máscaras negras es complejo. 
    # Dibujamos marcos superpuestos con alphas aditivos.
    
    # Rectángulo Amarillo Grande (Cubre Z1, Z2, Z3)
    r1 = Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FFFF00', alpha=0.05, zorder=1)
    ax.add_patch(r1)
    # Máscara negra para el centro seguro (Z0)
    m_safe = sim.dist_zone1
    safe_zone = Rectangle((-WIDTH/2 + m_safe, -HEIGHT/2 + m_safe), WIDTH-2*m_safe, HEIGHT-2*m_safe, color='#050505', zorder=10)
    ax.add_patch(safe_zone)
    
    # Rectángulo Naranja (Cubre Z2, Z3) - Solo en los bordes < 8m
    # Como es difícil hacer un "marco" con Rectangle, dibujamos 4 rectángulos o usamos la lógica de superposición.
    # Vamos a dibujar rectángulos llenos y tapar el centro.
    
    # Rectángulo Naranja (Cubre hasta 8m)
    # NO podemos dibujar rectángulos llenos porque se sumarían los alphas en el centro.
    # Pero como tenemos la safe_zone negra encima (zorder 10), no importa lo que pase en el centro.
    
    # Z2 (Naranja) - Debe verse entre 8m y 3m del borde.
    # Z3 (Roja) - Debe verse entre 3m y 0m del borde.
    
    # Dibujamos Rect Naranja que cubre Z2+Z3. Z1 ya está abajo.
    # Para que Z1 se vea solo entre 15 y 8, el naranja debe taparlo? No, se suman colores.
    
    # Enfoque simple:
    # 1. Marco Amarillo (15m a 0m)
    # 2. Marco Naranja (8m a 0m) -> Se suma al amarillo = Naranja amarillento
    # 3. Marco Rojo (3m a 0m) -> Se suma a todo = Rojo intenso
    
    # Capa 1: Amarillo (Z1+Z2+Z3)
    ax.add_patch(Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FFFF00', alpha=0.05, zorder=1))
    
    # Capa 2: Naranja (Z2+Z3) - Margin 8m
    m2 = 15.0 - 8.0 # No, margin es desde el borde.
    # El rect naranja debe empezar en el borde y terminar en dist_zone2 hacia adentro? No.
    # Debe ser un hueco.
    
    # Usaremos PathPatch para hacer marcos huecos perfectos (complicado sin importar más libs).
    # Volvamos a la máscara negra central escalonada.
    
    # Z1 (Amarillo) visible de 15 a 8.
    # Dibujamos amarillo de 15 a 0. Tapamos de 8 a 0 con Negro? No, queremos ver el naranja.
    
    # OK, SOLUCIÓN DEFINITIVA VISUAL:
    # Dibujamos 3 recuadros concéntricos de ATRÁS ADELANTE.
    
    # 1. Recuadro Rojo (Ocupa toda la pantalla)
    ax.add_patch(Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, color='#FF0000', alpha=0.2, zorder=1))
    
    # 2. Recuadro Naranja (Tapa el rojo en el centro, dejando solo un marco rojo de 3m)
    m3 = sim.dist_zone3
    ax.add_patch(Rectangle((-WIDTH/2 + m3, -HEIGHT/2 + m3), WIDTH - 2*m3, HEIGHT - 2*m3, color='#FF8800', alpha=0.2, zorder=2))
    
    # 3. Recuadro Amarillo (Tapa el naranja, dejando marco naranja de 5m)
    m2 = sim.dist_zone2
    ax.add_patch(Rectangle((-WIDTH/2 + m2, -HEIGHT/2 + m2), WIDTH - 2*m2, HEIGHT - 2*m2, color='#FFFF00', alpha=0.1, zorder=3))
    
    # 4. Recuadro Negro (Tapa el amarillo, dejando marco amarillo de 7m)
    m1 = sim.dist_zone1
    ax.add_patch(Rectangle((-WIDTH/2 + m1, -HEIGHT/2 + m1), WIDTH - 2*m1, HEIGHT - 2*m1, color='#050505', zorder=4))

    # Borde Físico
    wall_rect = Rectangle((-WIDTH/2, -HEIGHT/2), WIDTH, HEIGHT, 
                          linewidth=3, edgecolor='#888888', facecolor='none', zorder=5)
    ax.add_patch(wall_rect)

    title_text = (f"DEFENSA POR CAPAS (PERMISIVA)\n"
                  f"Amarillo: Brisa | Naranja: Presión | Rojo: Peligro | Gris: Choque")
    ax.text(0.02, 0.98, title_text, transform=ax.transAxes, color="gray", fontsize=11, va='top', family='monospace')

    birds = ax.quiver(sim.pos[:,0], sim.pos[:,1], sim.vel[:,0], sim.vel[:,1],
                      color='cyan', scale=None, scale_units='xy', angles='xy',
                      headlength=4.5, headaxislength=3.5, headwidth=3.5,
                      width=0.003, minshaft=0, pivot='mid', zorder=20)

    vectors = None
    if show_arrows:
        vectors = ax.quiver(sim.pos[:,0], sim.pos[:,1], sim.vel[:,0], sim.vel[:,1],
                            color='white', alpha=0.4, width=0.0015, scale=25, 
                            headlength=0, headaxislength=0, zorder=15)

    def update(frame):
        sim.step()
        birds.set_offsets(sim.pos)
        vis_vel = sim.vel / np.linalg.norm(sim.vel, axis=1, keepdims=True)
        birds.set_UVC(vis_vel[:,0], vis_vel[:,1])
        
        colors = np.array([[0, 1, 1, 0.8]] * sim.N) 
        active_leaders = sim.is_leader & (~sim.is_tired)
        colors[active_leaders] = [1, 0.3, 0, 1] 
        colors[sim.is_tired] = [0.5, 0.5, 0.5, 0.6] 
        birds.set_facecolors(colors)
        
        if show_arrows:
            vectors.set_offsets(sim.pos)
            vectors.set_UVC(sim.vel[:,0], sim.vel[:,1])
            return birds, vectors
        return birds,

    ani = animation.FuncAnimation(fig, update, frames=None, interval=20, blit=False, cache_frame_data=False)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--arrows", action="store_true", help="Mostrar vectores de dirección")
    args = parser.parse_args()
    
    run_simulation(show_arrows=args.arrows)