package representation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BirdFlock3D extends JPanel implements ActionListener {
    
	// Nombre d'oiseaux
    final int N_BIRDS = 450;
    // Taille de la boîte
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
        
        // Option DEBUG du prédateur
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
        double dt = 0.02;
        
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
        
        // Rotation autour de l'axe Y (Azimut)
        // x' = x * cos(θy) - z * sin(θy)
        // z' = x * sin(θy) + z * cos(θy)
        double tx = x*Math.cos(camAngleY) - z*Math.sin(camAngleY);
        double tz = x*Math.sin(camAngleY) + z*Math.cos(camAngleY);
        x=tx; z=tz;
        
        // Rotation autour de l'axe X (Élévation)
        // y' = y * cos(θx) - z * sin(θx)
        // z'' = y * sin(θx) + z * cos(θx)
        double ty = y*Math.cos(camAngleX) - z*Math.sin(camAngleX);
        tz = y*Math.sin(camAngleX) + z*Math.cos(camAngleX);
        y=ty; z=tz;
        
        // Translation caméra (profondeur)
        z += camDist;
        
        // Projection Perspective (Thalès)
        // Echelle = Focale / Profondeur
        // X_ecran = X_monde * Echelle
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
        
        // Affichage de la boîte
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

        // Affichage des particules
        for(DustParticle p : particles) {
            double z = getZ(p.pos);
            if(z<10) continue;
            // Transparence basée sur la profondeur (Fog)
            // Alpha = k / z
            int alpha = (int)(255 * (600/z));
            if(alpha > 60) alpha = 60;
            if(alpha < 5) alpha = 5;
            renderList.add(new RenderItem(new RenderDot(z, new Color(200, 200, 255, alpha), project(p.pos))));
        }

        // Affichage de la nourriture
        double zFood = getZ(food.pos);
        Point pFood = project(food.pos);
        // Taille apparente = TailleRéelle / Z
        double sizeFood = 20000 / zFood;
        double scentFood = (food.scentRadius * 2 * 1000) / zFood;
        renderList.add(new RenderItem(new RenderOval(zFood+1, new Color(0, 255, 0, 30), pFood, (int)scentFood/2, false)));
        renderList.add(new RenderItem(new RenderOval(zFood, Color.GREEN, pFood, (int)sizeFood/2, true)));

        // Affichage des oiseaux
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
        
        // Affichage du prédateur
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

    // Lancement
    public static void main(String[] args) {
        JFrame f = new JFrame("Simulation Nuée 3D - Final");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new BirdFlock3D());
        f.setSize(1280, 800);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}