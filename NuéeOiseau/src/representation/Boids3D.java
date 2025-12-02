package representation;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.DrawMode;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SIMULATION ORGANIQUE DE BOIDS 3D (JavaFX)
 * Version FLUIDE : Ajout d'une limite de force (Steering) pour supprimer les tremblements.
 */
public class Boids3D extends Application {

    // --- PARAMÈTRES ---
    private static final int N_BOIDS = 500;
    private static final double CUBE_SIZE = 3000; 
    private static final double WALL_MARGIN = 80.0; 

    // Physique Boids
    private static final double V0 = 3.0; // Vitesse de croisière
    
    // C'est cette variable qui supprime le tremblement.
    // Plus elle est basse, plus le virage est doux (et large).
    // Plus elle est haute, plus l'oiseau est nerveux.
    private static final double MAX_FORCE = 0.05; 

    // --- REGLAGES "GROUPE AÉRÉ & FLUIDE" ---
    private static final double R_SEP = 45.0; // Bulle personnelle
    private static final double R_PER = 90.0; // Perception
    private static final double FOV_DEG = 300.0; 

    // Multiplicateurs de forces
    private static final double WEIGHT_SEP = 2.5;  // Séparation prioritaire
    private static final double WEIGHT_ALI = 1.0;  // Alignement
    private static final double WEIGHT_COH = 1.2;  // Cohésion
    
    private static final double K_WALL = 15.0;   // Force des murs
    private static final double K_CENTER = 0.1;  // Force anti-sticking
    
    private final List<Boid> boids = new ArrayList<>();
    private final Random rand = new Random();

    @Override
    public void start(Stage stage) {
        Group root = new Group();

        // Environnement (Cube fil de fer blanc)
        Box boundaries = new Box(CUBE_SIZE, CUBE_SIZE, CUBE_SIZE);
        boundaries.setMaterial(new PhongMaterial(Color.WHITE));
        boundaries.setDrawMode(DrawMode.LINE); 
        boundaries.setCullFace(CullFace.NONE);
        root.getChildren().add(boundaries);

        // Zone Murs (Fil de fer rouge)
        Box dangerZone = new Box(CUBE_SIZE - (WALL_MARGIN*2), CUBE_SIZE - (WALL_MARGIN*2), CUBE_SIZE - (WALL_MARGIN*2));
        dangerZone.setMaterial(new PhongMaterial(Color.RED));
        dangerZone.setDrawMode(DrawMode.LINE); 
        root.getChildren().add(dangerZone);

        // Création des Boids
        for (int i = 0; i < N_BOIDS; i++) {
            Boid b = new Boid();
            boids.add(b);
            root.getChildren().add(b.mesh);
        }

        // Caméra
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setTranslateZ(-7000); 
        camera.setNearClip(0.1);
        camera.setFarClip(15000.0);

        Scene scene = new Scene(root, 1200, 800, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.rgb(20, 20, 25)); 
        scene.setCamera(camera);

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateSimulation();
            }
        }.start();

        initMouseControl(root, scene);

        stage.setTitle("Boids 3D - Mouvement Fluide");
        stage.setScene(scene);
        stage.show();
    }

    private void updateSimulation() {
        for (Boid b : boids) b.flock(boids);
        for (Boid b : boids) {
            b.updatePhysics();
            b.updateVisuals();
        }
    }

    // Vecteur 3D Mathématique
    static class Vec3 {
        double x, y, z;
        Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        void add(Vec3 v) { x += v.x; y += v.y; z += v.z; }
        void sub(Vec3 v) { x -= v.x; y -= v.y; z -= v.z; }
        void scale(double s) { x *= s; y *= s; z *= s; }
        double mag() { return Math.sqrt(x*x + y*y + z*z); }
        void normalize() {
            double m = mag();
            if (m != 0 && m != 1) scale(1/m);
        }
        void limit(double max) {
            if (mag() > max) { normalize(); scale(max); }
        }
        double dot(Vec3 v) { return x*v.x + y*v.y + z*v.z; }
        static Vec3 sub(Vec3 a, Vec3 b) { return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z); }
    }

    // Classe Oiseau
    class Boid {
        Vec3 pos, vel, acc;
        Cylinder mesh; 

        Boid() {
            double range = (CUBE_SIZE / 2) - WALL_MARGIN - 10;
            pos = new Vec3(
                (rand.nextDouble() * 2 - 1) * range,
                (rand.nextDouble() * 2 - 1) * range,
                (rand.nextDouble() * 2 - 1) * range
            );
            vel = new Vec3(rand.nextDouble()-0.5, rand.nextDouble()-0.5, rand.nextDouble()-0.5);
            vel.normalize(); 
            vel.scale(V0);
            acc = new Vec3(0, 0, 0);

            mesh = new Cylinder(3, 15); 
            PhongMaterial mat = new PhongMaterial(Color.CYAN);
            mat.setSpecularColor(Color.WHITE);
            mesh.setMaterial(mat);
        }

        void flock(List<Boid> boids) {
            Vec3 sep = new Vec3(0,0,0);
            Vec3 ali = new Vec3(0,0,0);
            Vec3 coh = new Vec3(0,0,0);
            int total = 0;
            double fovThreshold = Math.cos(Math.toRadians(FOV_DEG / 2.0));

            for (Boid other : boids) {
                if (other == this) continue;
                double d = dist(this.pos, other.pos);

                if (d < R_PER && d > 0) {
                    Vec3 toNeighbor = Vec3.sub(other.pos, this.pos);
                    toNeighbor.normalize();
                    Vec3 myDir = new Vec3(vel.x, vel.y, vel.z);
                    myDir.normalize();

                    // Si dans le champ de vision
                    if (myDir.dot(toNeighbor) > fovThreshold) {
                        
                        // 1. Alignement (Moyenne des vitesses)
                        ali.add(other.vel);
                        
                        // 2. Cohésion (Moyenne des positions)
                        coh.add(other.pos);
                        
                        // 3. Séparation
                        if (d < R_SEP) {
                            Vec3 diff = Vec3.sub(this.pos, other.pos);
                            diff.normalize();
                            diff.scale(1.0 / d); // Linéaire au lieu de quadratique pour moins de violence
                            sep.add(diff);
                        }
                        total++;
                    }
                }
            }

            if (total > 0) {
                // --- ALGORITHME DE REYNOLDS (STEERING) ---
                // Au lieu d'ajouter brutalement la force, on calcule :
                // Steering = Desired - Velocity
                
                // Alignement
                ali.scale(1.0 / total);
                ali.normalize();
                ali.scale(V0);
                ali.sub(vel);
                ali.limit(MAX_FORCE);
                ali.scale(WEIGHT_ALI);

                // Cohésion
                coh.scale(1.0 / total);
                coh.sub(pos); // Vecteur vers le centre local
                coh.normalize();
                coh.scale(V0);
                coh.sub(vel); // Steering vers la cible
                coh.limit(MAX_FORCE);
                coh.scale(WEIGHT_COH);

                // Séparation
                sep.scale(1.0 / total);
                sep.normalize();
                sep.scale(V0);
                sep.sub(vel);
                sep.limit(MAX_FORCE * 1.5); // On autorise une réaction plus vive pour la séparation
                sep.scale(WEIGHT_SEP);
            }

            // Murs (Priorité absolue, on l'ajoute directement)
            Vec3 wallForce = calculateWallForce();
            
            acc.add(ali);
            acc.add(coh);
            acc.add(sep);
            acc.add(wallForce);
        }

        Vec3 calculateWallForce() {
            Vec3 f = new Vec3(0,0,0);
            double limit = CUBE_SIZE / 2.0;
            boolean inDanger = false;

            f.x += getExpForce(limit - pos.x, -1);
            f.x += getExpForce(pos.x - (-limit), 1);
            if (Math.abs(pos.x) > limit - WALL_MARGIN) inDanger = true;

            f.y += getExpForce(limit - pos.y, -1);
            f.y += getExpForce(pos.y - (-limit), 1);
            if (Math.abs(pos.y) > limit - WALL_MARGIN) inDanger = true;

            f.z += getExpForce(limit - pos.z, -1);
            f.z += getExpForce(pos.z - (-limit), 1);
            if (Math.abs(pos.z) > limit - WALL_MARGIN) inDanger = true;

            if (inDanger) {
                Vec3 toCenter = new Vec3(-pos.x, -pos.y, -pos.z);
                toCenter.normalize();
                toCenter.scale(K_CENTER);
                f.add(toCenter);
            }
            return f;
        }

        double getExpForce(double d, int dir) {
            if (d < WALL_MARGIN) {
                d = Math.max(d, 0.1); 
                double val = K_WALL * Math.pow((WALL_MARGIN - d) / d, 2);
                return val * dir;
            }
            return 0;
        }

        void updatePhysics() {
            vel.add(acc);
            vel.limit(V0);
            // On force une vitesse minimale pour éviter qu'ils fassent du surplace
            if (vel.mag() < V0 * 0.5) { 
                vel.normalize(); vel.scale(V0 * 0.5); 
            }
            pos.add(vel);
            acc.scale(0); 
            
            // Hard Limit
            double L = CUBE_SIZE/2;
            if (pos.x > L) pos.x = L; if (pos.x < -L) pos.x = -L;
            if (pos.y > L) pos.y = L; if (pos.y < -L) pos.y = -L;
            if (pos.z > L) pos.z = L; if (pos.z < -L) pos.z = -L;
        }

        void updateVisuals() {
            mesh.setTranslateX(pos.x);
            mesh.setTranslateY(pos.y);
            mesh.setTranslateZ(pos.z);

            Point3D velocity = new Point3D(vel.x, vel.y, vel.z);
            Point3D Y_AXIS = new Point3D(0, 1, 0);
            
            // Lissage visuel (Interpolation) si nécessaire, mais avec MAX_FORCE
            // l'orientation devrait déjà être fluide.
            if (velocity.magnitude() > 0.1) {
                Point3D axis = Y_AXIS.crossProduct(velocity);
                double angle = Y_AXIS.angle(velocity);
                mesh.setRotationAxis(axis);
                mesh.setRotate(angle);
            }
        }
    }

    double dist(Vec3 a, Vec3 b) {
        return Math.sqrt(Math.pow(a.x-b.x, 2) + Math.pow(a.y-b.y, 2) + Math.pow(a.z-b.z, 2));
    }

    private double anchorX, anchorY;
    private double anchorAngleX = 0;
    private double anchorAngleY = 0;
    private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

    private void initMouseControl(Group group, Scene scene) {
        group.getTransforms().addAll(rotateX, rotateY);
        scene.setOnMousePressed(e -> {
            anchorX = e.getSceneX(); anchorY = e.getSceneY();
            anchorAngleX = rotateX.getAngle(); anchorAngleY = rotateY.getAngle();
        });
        scene.setOnMouseDragged(e -> {
            rotateX.setAngle(anchorAngleX - (e.getSceneY() - anchorY));
            rotateY.setAngle(anchorAngleY + (e.getSceneX() - anchorX));
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}