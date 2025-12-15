package representation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// =====================================================================
// VECTEUR 3D
// =====================================================================
class Vector3D {
    public double x, y, z;

    public Vector3D(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public Vector3D copy() { return new Vector3D(x, y, z); }
    public void add(Vector3D v) { x += v.x; y += v.y; z += v.z; }
    public void sub(Vector3D v) { x -= v.x; y -= v.y; z -= v.z; }
    public void mult(double n) { x *= n; y *= n; z *= n; }
    public void div(double n) { x /= n; y /= n; z /= n; }
    
    public double mag() { return Math.sqrt(x*x + y*y + z*z); }
    public double magSq() { return x*x + y*y + z*z; }
    
    public void normalize() {
        double m = mag();
        if (m > 0) div(m);
    }
    
    public void limit(double max) {
        if (magSq() > max * max) {
            normalize();
            mult(max);
        }
    }

    public void setMag(double len) {
        normalize();
        mult(len);
    }

    public double dot(Vector3D v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public static Vector3D sub(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
    }

    public static double distSq(Vector3D v1, Vector3D v2) {
        return Math.pow(v1.x - v2.x, 2) + Math.pow(v1.y - v2.y, 2) + Math.pow(v1.z - v2.z, 2);
    }

    public static Vector3D cross(Vector3D v1, Vector3D v2) {
        return new Vector3D(
            v1.y * v2.z - v1.z * v2.y,
            v1.z * v2.x - v1.x * v2.z,
            v1.x * v2.y - v1.y * v2.x
        );
    }
}

// =====================================================================
// VENT & PARTICULES
// =====================================================================
class WindField {
    double time = 0;
    
    public void update(double dt) {
        time += dt;
    }

    public Vector3D getForce(Vector3D pos) {
        double scale = 0.003;
        double angleY = pos.x * scale + time * 0.5;
        double angleX = pos.y * scale + time * 0.3;
        double angleZ = pos.z * scale + time * 0.4;

        double wx = Math.cos(angleX);
        double wy = Math.sin(angleZ); 
        double wz = Math.cos(angleY); 

        Vector3D w = new Vector3D(wx, wy, wz);
        w.mult(0.15); 
        return w;
    }
}

class DustParticle {
    Vector3D pos;
    Random r = new Random();
    
    public DustParticle(double boxSize) {
        respawn(boxSize);
    }
    
    public void respawn(double boxSize) {
        pos = new Vector3D(
            (r.nextDouble()*2-1) * boxSize/2,
            (r.nextDouble()*2-1) * boxSize/2,
            (r.nextDouble()*2-1) * boxSize/2
        );
    }
    
    public void update(WindField wind, double dt, double boxSize) {
        Vector3D w = wind.getForce(pos);
        w.mult(30.0); 
        pos.add(w);
        
        if (Math.abs(pos.x) > boxSize/2) pos.x *= -0.95;
        if (Math.abs(pos.y) > boxSize/2) pos.y *= -0.95;
        if (Math.abs(pos.z) > boxSize/2) pos.z *= -0.95;
    }
}

// =====================================================================
// NOURRITURE
// =====================================================================
class FoodResource {
    Vector3D pos;
    double limit;
    double scentRadius = 350;
    Random r = new Random();
    
    public FoodResource(double boxSize) {
        this.limit = boxSize / 2 - 100;
        respawn();
    }
    public void respawn() {
        pos = new Vector3D((r.nextDouble()*2-1)*limit, (r.nextDouble()*2-1)*limit, (r.nextDouble()*2-1)*limit);
    }
}

// =====================================================================
// PRÉDATEUR
// =====================================================================
class Predator {
    Vector3D pos, vel;
    double huntSpeed = 9.0; 
    double digestSpeed = 3.5;
    
    enum State { HUNTING, DIGESTING }
    State state = State.HUNTING;
    
    int totalBirdsEaten = 0;
    int sessionBirdsEaten = 0;
    final int HUNGER_QUOTA = 2; 
    
    double digestionTimer = 0;
    double roarRadius = 300; 
    boolean isRoaring = false;
    double roarTimer = 0;
    
    Random rand = new Random();
    
    public Predator() {
        resetPosition();
        resetHunt();
    }
    
    public void resetPosition() {
        pos = new Vector3D(0,0,0);
        vel = new Vector3D(1, 1, 1);
    }
    
    private void resetHunt() {
        state = State.HUNTING;
        sessionBirdsEaten = 0;
        triggerRoar();
    }
    
    private void triggerRoar() {
        isRoaring = true; 
        roarTimer = 2.0; 
    }
    
    public void update(List<Bird> birds, double dt, double boxSize, Vector3D windForce) {
        if (isRoaring) {
            roarTimer -= dt;
            if (roarTimer <= 0) isRoaring = false;
        }

        Vector3D acc = new Vector3D(0,0,0);
        
        if (state == State.DIGESTING) {
            digestionTimer -= dt;
            if (digestionTimer <= 0) {
                resetHunt();
            } else {
                Vector3D wander = new Vector3D(rand.nextDouble()-0.5, rand.nextDouble()-0.5, rand.nextDouble()-0.5);
                wander.normalize();
                wander.mult(0.6);
                acc.add(wander);
            }
        } else {
            Bird closest = null;
            double minDist = Double.MAX_VALUE;
            for (Bird b : birds) {
                double d = Vector3D.distSq(pos, b.pos);
                if (d < minDist) { minDist = d; closest = b; }
            }
            
            if (closest != null) {
                Vector3D desired = Vector3D.sub(closest.pos, pos);
                desired.normalize();
                desired.mult(huntSpeed);
                Vector3D steer = Vector3D.sub(desired, vel);
                steer.limit(0.4); 
                acc.add(steer);
            }
            if (!isRoaring && rand.nextDouble() < 0.005) triggerRoar();
        }
        
        acc.add(windForce);
        vel.add(acc);
        
        double currentMax = (state == State.HUNTING) ? huntSpeed : digestSpeed;
        vel.limit(currentMax);
        
        if (state == State.DIGESTING && vel.mag() > digestSpeed) vel.setMag(digestSpeed);

        pos.add(vel);
        
        double limit = boxSize / 2;
        if (pos.x > limit) { pos.x = limit; vel.x *= -1; }
        else if (pos.x < -limit) { pos.x = -limit; vel.x *= -1; }
        
        if (pos.y > limit) { pos.y = limit; vel.y *= -1; }
        else if (pos.y < -limit) { pos.y = -limit; vel.y *= -1; }
        
        if (pos.z > limit) { pos.z = limit; vel.z *= -1; }
        else if (pos.z < -limit) { pos.z = -limit; vel.z *= -1; }
    }
    
    public boolean checkEat(List<Bird> birds) {
        if (state != State.HUNTING) return false;
        
        for (int i=0; i<birds.size(); i++) {
            if (Vector3D.distSq(pos, birds.get(i).pos) < 169) { 
                birds.get(i).respawn();
                sessionBirdsEaten++;
                totalBirdsEaten++;
                
                if (sessionBirdsEaten >= HUNGER_QUOTA) {
                    state = State.DIGESTING;
                    digestionTimer = 10.0;
                }
                return true;
            }
        }
        return false;
    }
}

// =====================================================================
// OISEAU (FINAL: LEADERSHIP & FATIGUE)
// =====================================================================
class Bird {
    Vector3D pos, vel, acc;
    
    // États
    boolean isLeader = false;
    boolean isTired = false;
    boolean isAlerted = false;
    
    double maxSpeed = 5.5;
    double maxForce = 0.15;
    
    double stamina = 100;
    double timeSpentLeading = 0; // Temps passé en tête
    
    double perception = 85.0;
    double separationRad = 28.0;
    
    public Bird(double x, double y, double z) {
        pos = new Vector3D(x, y, z);
        Random r = new Random();
        vel = new Vector3D(r.nextDouble()-0.5, r.nextDouble()-0.5, r.nextDouble()-0.5);
        vel.normalize();
        vel.mult(maxSpeed);
        acc = new Vector3D(0,0,0);
    }
    
    public void respawn() {
        Random r = new Random();
        pos = new Vector3D((r.nextDouble()*2-1)*400, (r.nextDouble()*2-1)*400, (r.nextDouble()*2-1)*400);
        stamina = 100;
        isTired = false;
        timeSpentLeading = 0;
    }
    
    public void applyForce(Vector3D f) { acc.add(f); }

    public void flock(List<Bird> birds, Predator pred, FoodResource food, double boxSize, double dt, Vector3D wind) {
        Vector3D sep = new Vector3D(0,0,0);
        Vector3D ali = new Vector3D(0,0,0);
        Vector3D coh = new Vector3D(0,0,0);
        
        int neighborsCount = 0;
        int neighborsInFront = 0;
        Vector3D myHeading = vel.copy(); myHeading.normalize();
        
        for (Bird other : birds) {
            if (other == this) continue;
            if (Math.abs(other.pos.x - pos.x) > perception) continue; 
            
            double dSq = Vector3D.distSq(pos, other.pos);
            if (dSq < perception * perception && dSq > 0) {
                ali.add(other.vel);
                coh.add(other.pos);
                if (dSq < separationRad * separationRad) {
                    Vector3D diff = Vector3D.sub(pos, other.pos);
                    diff.normalize();
                    diff.div(Math.sqrt(dSq)); 
                    sep.add(diff);
                }
                
                // Calcul Voisins Devant (Aspiration)
                Vector3D toOther = Vector3D.sub(other.pos, pos);
                toOther.normalize();
                if (myHeading.dot(toOther) > 0.3) neighborsInFront++;
                neighborsCount++;
            }
        }
        
        // --- LOGIQUE LEADERSHIP & FATIGUE ---
        // Je suis leader si je suis dans un groupe (>2) mais que personne n'est devant moi
        boolean isGeometricLeader = (neighborsCount > 2 && neighborsInFront == 0);
        
        if (isGeometricLeader && !isTired) {
            isLeader = true;
            // Si je suis leader, je me fatigue (je prends le vent)
            timeSpentLeading += dt;
            
            // Au bout de ~10 secondes de leadership, je craque
            if (timeSpentLeading > 10.0) {
                isTired = true; // Je deviens gris
                stamina = 0;    // Plus d'énergie
                timeSpentLeading = 0; // Reset
            }
        } else {
            isLeader = false;
            timeSpentLeading = 0;
        }
        
        if (neighborsCount > 0) {
            ali.div(neighborsCount); ali.setMag(maxSpeed); ali.sub(vel); ali.limit(maxForce);
            coh.div(neighborsCount); coh.sub(pos); coh.setMag(maxSpeed); coh.sub(vel); coh.limit(maxForce);
            sep.div(neighborsCount); sep.setMag(maxSpeed); sep.sub(vel); sep.limit(maxForce * 2.5);
        }
        
        // --- DETECTION PRÉDATEUR ---
        this.isAlerted = false;
        if (pred.state == Predator.State.HUNTING) {
            double distPredSq = Vector3D.distSq(pos, pred.pos);
            if (distPredSq < 130 * 130) isAlerted = true;
            if (pred.isRoaring && distPredSq < pred.roarRadius * pred.roarRadius) isAlerted = true;

            if (this.isAlerted) {
                Vector3D flee = Vector3D.sub(pos, pred.pos);
                flee.normalize();
                flee.mult(6.0); 
                applyForce(flee);
            }
        }

        // --- STAMINA (RECOUVREMENT) ---
        // Si fatigué ou en fuite, on consomme/reste bas
        if (isAlerted && !isTired) {
            stamina -= 1.5; 
            if (stamina <= 0) { stamina = 0; isTired = true; }
        } else {
            // Récupération naturelle
            stamina += 0.4; 
            if (stamina >= 100) { stamina = 100; isTired = false; }
        }

        Vector3D foodForce = new Vector3D(0,0,0);
        if (Vector3D.distSq(pos, food.pos) < food.scentRadius*food.scentRadius) {
            Vector3D dir = Vector3D.sub(food.pos, pos);
            dir.normalize(); dir.mult(0.5); foodForce = dir;
        }

        Vector3D wallForce = new Vector3D(0,0,0);
        double margin = 100;
        double limit = boxSize/2;
        if (pos.x > limit - margin) wallForce.x = -1.5;
        if (pos.x < -limit + margin) wallForce.x = 1.5;
        if (pos.y > limit - margin) wallForce.y = -1.5;
        if (pos.y < -limit + margin) wallForce.y = 1.5;
        if (pos.z > limit - margin) wallForce.z = -1.5;
        if (pos.z < -limit + margin) wallForce.z = 1.5;

        sep.mult(2.8); ali.mult(1.0); coh.mult(0.8);
        applyForce(sep); applyForce(ali); applyForce(coh);
        applyForce(wind); applyForce(foodForce); applyForce(wallForce);
    }

    public void update(double dt, double boxSize) {
        vel.add(acc);
        
        double currentMax = maxSpeed;
        double currentMin = 3.5;
        
        // GESTION VITESSE PAR ÉTAT
        if (isTired) {
            // L'oiseau fatigué ralentit significativement
            // Cela permet aux autres (non fatigués) de le doubler
            currentMax = maxSpeed * 0.5; 
            currentMin = 1.0;
        } else if (isAlerted) {
            currentMax = maxSpeed * 1.5; 
        }

        double speed = vel.mag();
        if (speed < 0.0001) speed = 0.0001;
        if (speed > currentMax) vel.setMag(currentMax);
        else if (speed < currentMin) vel.setMag(currentMin);
        
        pos.add(vel);
        acc.mult(0);
        
        double limit = boxSize/2;
        if (pos.x > limit) pos.x = limit; if (pos.x < -limit) pos.x = -limit;
        if (pos.y > limit) pos.y = limit; if (pos.y < -limit) pos.y = -limit;
        if (pos.z > limit) pos.z = limit; if (pos.z < -limit) pos.z = -limit;
    }
}

// =====================================================================
// MAIN & RENDU
// =====================================================================
public class BirdFlock3D extends JPanel implements ActionListener {
    
    final int N_BIRDS = 450;
    final double BOX_SIZE = 1200;
    
    List<Bird> birds;
    Predator predator;
    FoodResource food;
    WindField windField;
    List<DustParticle> particles;
    
    Timer timer;
    
    double camAngleY = 0.6, camAngleX = 0.4;
    double camDist = 2200;
    int mouseX, mouseY;

    public BirdFlock3D() {
        setLayout(new BorderLayout());
        
        birds = new ArrayList<>();
        predator = new Predator();
        food = new FoodResource(BOX_SIZE);
        windField = new WindField();
        particles = new ArrayList<>();
        
        Random r = new Random();
        for (int i = 0; i < N_BIRDS; i++) {
            birds.add(new Bird(
                (r.nextDouble()-0.5) * BOX_SIZE * 0.7,
                (r.nextDouble()-0.5) * BOX_SIZE * 0.7,
                (r.nextDouble()-0.5) * BOX_SIZE * 0.7
            ));
        }
        
        for(int i=0; i<300; i++) particles.add(new DustParticle(BOX_SIZE));
        
        setBackground(new Color(15, 15, 22));
        setDoubleBuffered(true);
        setFocusable(true); 
        
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { 
                mouseX = e.getX(); mouseY = e.getY(); 
                requestFocusInWindow(); 
            }
            public void mouseDragged(MouseEvent e) {
                camAngleY += (e.getX() - mouseX) * 0.005;
                camAngleX += (e.getY() - mouseY) * 0.005;
                mouseX = e.getX(); mouseY = e.getY();
            }
            public void mouseWheelMoved(MouseWheelEvent e) {
                camDist += e.getWheelRotation() * 100;
                if (camDist < 200) camDist = 200;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        
        // Panel Debug
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        bottomPanel.setBackground(new Color(25, 25, 35));
        
        JButton resetButton = new JButton("Debug: Reset Prédateur");
        resetButton.setFocusable(false);
        resetButton.addActionListener(e -> {
            predator.resetPosition();
            requestFocusInWindow();
        });
        bottomPanel.add(resetButton);
        
        add(bottomPanel, BorderLayout.SOUTH);

        timer = new Timer(20, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        double dt = 0.02; // Temps fixe pour la simulation (50 FPS)
        
        windField.update(dt * 2.0);
        
        Vector3D predWind = windField.getForce(predator.pos);
        predator.update(birds, dt, BOX_SIZE, predWind);
        predator.checkEat(birds);
        
        for(DustParticle p : particles) p.update(windField, dt, BOX_SIZE);
        
        for (Bird b : birds) {
            Vector3D w = windField.getForce(b.pos);
            b.flock(birds, predator, food, BOX_SIZE, dt, w);
            b.update(dt, BOX_SIZE);
            if (Vector3D.distSq(b.pos, food.pos) < 600) food.respawn();
        }
        repaint();
    }
    
    interface Drawable3D {
        double getZ();
        void draw(Graphics2D g);
    }
    
    class RenderPolygon implements Drawable3D, Comparable<RenderPolygon> {
        double z; Color c; Polygon p;
        public RenderPolygon(double z, Color c, Polygon p) { this.z = z; this.c = c; this.p = p; }
        public double getZ() { return z; }
        public void draw(Graphics2D g) { g.setColor(c); g.fillPolygon(p); }
        public int compareTo(RenderPolygon o) { return Double.compare(o.z, this.z); }
    }

    class RenderOval implements Drawable3D {
        double z; Color c; Point p; int r; boolean fill;
        public RenderOval(double z, Color c, Point p, int r, boolean fill) { this.z = z; this.c = c; this.p = p; this.r = r; this.fill = fill; }
        public double getZ() { return z; }
        public void draw(Graphics2D g) { 
            g.setColor(c); 
            if(fill) g.fillOval(p.x-r, p.y-r, r*2, r*2);
            else g.drawOval(p.x-r, p.y-r, r*2, r*2);
        }
    }
    
    class RenderDot implements Drawable3D {
        double z; Color c; Point p;
        public RenderDot(double z, Color c, Point p) { this.z = z; this.c = c; this.p = p; }
        public double getZ() { return z; }
        public void draw(Graphics2D g) { g.setColor(c); g.fillRect(p.x, p.y, 2, 2); }
    }

    Point project(Vector3D v) {
        double x = v.x, y = v.y, z = v.z;
        double tx = x*Math.cos(camAngleY) - z*Math.sin(camAngleY);
        double tz = x*Math.sin(camAngleY) + z*Math.cos(camAngleY);
        x=tx; z=tz;
        double ty = y*Math.cos(camAngleX) - z*Math.sin(camAngleX);
        tz = y*Math.sin(camAngleX) + z*Math.cos(camAngleX);
        y=ty; z=tz;
        z += camDist;
        double f = 1000;
        if (z<=10) z=10;
        double s = f/z;
        return new Point(getWidth()/2 + (int)(x*s), getHeight()/2 + (int)(y*s));
    }
    
    double getZ(Vector3D v) {
        double x = v.x, y = v.y, z = v.z;
        double tx = x*Math.cos(camAngleY) - z*Math.sin(camAngleY);
        double tz = x*Math.sin(camAngleY) + z*Math.cos(camAngleY);
        x=tx; z=tz;
        double ty = y*Math.cos(camAngleX) - z*Math.sin(camAngleX);
        tz = y*Math.sin(camAngleX) + z*Math.cos(camAngleX);
        return tz + camDist;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        class RenderItem implements Comparable<RenderItem> {
            double z; Drawable3D d;
            public RenderItem(Drawable3D d) { this.d = d; this.z = d.getZ(); }
            public int compareTo(RenderItem o) { return Double.compare(o.z, this.z); }
        }
        List<RenderItem> renderList = new ArrayList<>();
        
        // BOITE
        double sz = BOX_SIZE/2;
        Vector3D[] corners = {
            new Vector3D(-sz,-sz,-sz), new Vector3D(sz,-sz,-sz), new Vector3D(sz,sz,-sz), new Vector3D(-sz,sz,-sz),
            new Vector3D(-sz,-sz,sz), new Vector3D(sz,-sz,sz), new Vector3D(sz,sz,sz), new Vector3D(-sz,sz,sz)
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        g2.setColor(new Color(40, 40, 50));
        for(int[] ed : edges) {
            Point p1 = project(corners[ed[0]]);
            Point p2 = project(corners[ed[1]]);
            g2.drawLine(p1.x, p1.y, p2.x, p2.y);
        }

        // PARTICULES
        for(DustParticle p : particles) {
            double z = getZ(p.pos);
            if(z<10) continue;
            int alpha = (int)(255 * (600/z));
            if(alpha > 60) alpha = 60;
            if(alpha < 5) alpha = 5;
            renderList.add(new RenderItem(new RenderDot(z, new Color(200, 200, 255, alpha), project(p.pos))));
        }

        // NOURRITURE
        double zFood = getZ(food.pos);
        Point pFood = project(food.pos);
        double sizeFood = 20000 / zFood;
        double scentFood = (food.scentRadius * 2 * 1000) / zFood;
        renderList.add(new RenderItem(new RenderOval(zFood+1, new Color(0, 255, 0, 30), pFood, (int)scentFood/2, false)));
        renderList.add(new RenderItem(new RenderOval(zFood, Color.GREEN, pFood, (int)sizeFood/2, true)));

        // OISEAUX
        int panickedCount = 0;
        int tiredCount = 0;
        for (Bird b : birds) {
            double z = getZ(b.pos);
            if (z < 10) continue;
            
            if(b.isAlerted) panickedCount++;
            if(b.isTired) tiredCount++;

            Vector3D tip = b.pos.copy();
            Vector3D dir = b.vel.copy(); dir.normalize();
            Vector3D baseCenter = dir.copy(); baseCenter.mult(-15.0); baseCenter.add(tip);
            
            Vector3D up = new Vector3D(0,1,0);
            if (Math.abs(dir.y) > 0.9) up = new Vector3D(1,0,0);
            Vector3D right = Vector3D.cross(dir, up); right.normalize(); right.mult(5.0);
            Vector3D top = Vector3D.cross(right, dir); top.normalize(); top.mult(5.0);
            
            Point sp1 = project(tip);
            Point sp2 = project(Vector3D.sub(baseCenter, right));
            Point sp3 = project(new Vector3D(baseCenter.x+right.x, baseCenter.y+right.y, baseCenter.z+right.z));
            Point sp4 = project(new Vector3D(baseCenter.x+top.x, baseCenter.y+top.y, baseCenter.z+top.z));

            Color c = Color.CYAN;
            if (b.isTired) c = Color.GRAY;
            else if (b.isAlerted) c = Color.YELLOW;
            else if (b.isLeader) c = Color.RED;

            Polygon poly1 = new Polygon(); poly1.addPoint(sp1.x, sp1.y); poly1.addPoint(sp2.x, sp2.y); poly1.addPoint(sp3.x, sp3.y);
            renderList.add(new RenderItem(new RenderPolygon(z, c, poly1)));
            
            Polygon poly2 = new Polygon(); poly2.addPoint(sp1.x, sp1.y); poly2.addPoint(sp3.x, sp3.y); poly2.addPoint(sp4.x, sp4.y);
            renderList.add(new RenderItem(new RenderPolygon(z, c.darker(), poly2)));
        }
        
        // PRÉDATEUR
        {
            double z = getZ(predator.pos);
            Vector3D tip = predator.pos.copy();
            Vector3D dir = predator.vel.copy(); dir.normalize();
            Vector3D baseCenter = dir.copy(); baseCenter.mult(-35.0); baseCenter.add(tip);
            
            Vector3D up = new Vector3D(0,1,0);
            Vector3D right = Vector3D.cross(dir, up); right.normalize(); right.mult(12.0);
            Vector3D top = Vector3D.cross(right, dir); top.normalize(); top.mult(12.0);
            
            Point sp1 = project(tip);
            Point sp2 = project(Vector3D.sub(baseCenter, right));
            Point sp3 = project(new Vector3D(baseCenter.x+right.x, baseCenter.y+right.y, baseCenter.z+right.z));
            Point sp4 = project(new Vector3D(baseCenter.x+top.x, baseCenter.y+top.y, baseCenter.z+top.z));

            Color cPred = (predator.state == Predator.State.HUNTING) ? new Color(255, 50, 0) : new Color(180, 0, 255);
            
            Polygon p1 = new Polygon(); p1.addPoint(sp1.x, sp1.y); p1.addPoint(sp2.x, sp2.y); p1.addPoint(sp3.x, sp3.y);
            renderList.add(new RenderItem(new RenderPolygon(z, cPred, p1)));
            Polygon p2 = new Polygon(); p2.addPoint(sp1.x, sp1.y); p2.addPoint(sp3.x, sp3.y); p2.addPoint(sp4.x, sp4.y);
            renderList.add(new RenderItem(new RenderPolygon(z, cPred.darker(), p2)));
            
            if (predator.isRoaring) {
                Point center = project(predator.pos);
                double rad = (predator.roarRadius * 1000) / z;
                renderList.add(new RenderItem(new RenderOval(z+5, new Color(255,255,255,100), center, (int)rad, false)));
            }
        }
        
        Collections.sort(renderList);
        for(RenderItem item : renderList) item.d.draw(g2);
        
        drawHUD(g2, panickedCount, tiredCount);
    }
    
    private void drawHUD(Graphics2D g2, int panic, int tired) {
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        
        int x = 20, y = 20, w = 240, h = 100;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(Color.WHITE);
        g2.drawRoundRect(x, y, w, h, 10, 10);
        
        g2.drawString("--- PRÉDATEUR ---", x+10, y+20);
        g2.drawString("Mangés (Total)   : " + predator.totalBirdsEaten, x+10, y+40);
        g2.drawString("Quota Actuel     : " + predator.sessionBirdsEaten + "/" + predator.HUNGER_QUOTA, x+10, y+60);
        
        String pState = (predator.state == Predator.State.HUNTING) ? "CHASSE" : "DIGESTION";
        Color pColor = (predator.state == Predator.State.HUNTING) ? Color.RED : new Color(180, 0, 255);
        g2.drawString("État: ", x+10, y+80);
        g2.setColor(pColor); g2.drawString(pState, x+50, y+80);
        
        if (predator.state == Predator.State.DIGESTING) {
            g2.setColor(Color.WHITE);
            g2.drawString(String.format("(Reste: %.1fs)", predator.digestionTimer), x+130, y+80);
        }

        y += 110; h = 100;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(Color.WHITE);
        g2.drawRoundRect(x, y, w, h, 10, 10);
        
        g2.drawString("--- OISEAUX ---", x+10, y+20);
        g2.drawString("Population    : " + birds.size(), x+10, y+40);
        
        g2.setColor(Color.YELLOW);
        g2.drawString("Panique       : " + panic, x+10, y+60);
        g2.setColor(Color.GRAY);
        g2.drawString("Fatigués      : " + tired, x+10, y+80);
        
        y += 110; h = 80;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(Color.WHITE);
        
        g2.setColor(Color.RED); g2.drawString("■ Leader (Fatigue)", x+10, y+20);
        g2.setColor(Color.CYAN); g2.drawString("■ Suiveur", x+100, y+20);
        g2.setColor(Color.YELLOW); g2.drawString("■ Alerte", x+10, y+40);
        g2.setColor(Color.GRAY); g2.drawString("■ Épuisé", x+100, y+40);
        g2.setColor(Color.WHITE); g2.drawString(". Poussière (Vent)", x+10, y+60);
    }

    public static void main(String[] args) {
        JFrame f = new JFrame("Simulation Nuée 3D - Final");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new BirdFlock3D());
        f.setSize(1280, 800);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}