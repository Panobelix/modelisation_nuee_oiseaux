package representation;

import javafx.geometry.Point3D;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import java.util.List;
import java.util.Random;

public class Boid {
    private Point3D position;
    private Point3D velocity;
    private MeshView boidView;
    
    // --- Paramètres de simulation ---
    // Augmentez maxSpeed pour des mouvements plus rapides
    private double maxSpeed = 4.0; 
    private double maxForce = 0.1;
    // Augmentez le rayon pour qu'ils se détectent de plus loin
    private double perceptionRadius = 60.0; 
    
    private Random random = new Random();

    public Boid(double x, double y, double z, Color color) {
        this.position = new Point3D(x, y, z);
        // Vitesse initiale : légère poussée vers le centre (opposée à leur position initiale)
        this.velocity = new Point3D(-Math.signum(x), random.nextDouble() - 0.5, random.nextDouble() - 0.5)
                .normalize().multiply(maxSpeed);
        
        this.boidView = createBoidMesh(color);
        updateViewTransform();
    }

    // Création du maillage (identique avant, mais prend une couleur en paramètre)
    private MeshView createBoidMesh(Color color) {
        TriangleMesh mesh = new TriangleMesh();
        float size = 2.0f; // Un peu plus gros pour mieux voir

        mesh.getPoints().addAll(
            0, 0, size * 3,  // Pointe avant allongée
            size, 0, 0, -size, 0, 0, 0, size, 0, 0, -size, 0
        );
        mesh.getTexCoords().addAll(0, 0);
        mesh.getFaces().addAll(
            0, 0, 1, 0, 3, 0, 0, 0, 3, 0, 2, 0,
            0, 0, 2, 0, 4, 0, 0, 0, 4, 0, 1, 0
        );

        MeshView meshView = new MeshView(mesh);
        // Matériau un peu brillant pour le relief 3D
        PhongMaterial material = new PhongMaterial(color);
        material.setSpecularColor(Color.WHITE);
        meshView.setMaterial(material);
        return meshView;
    }

    public void update(List<Boid> boids) {
        Point3D flockForce = calculateFlockingForces(boids);
        
        // --- NOUVELLE RÈGLE : Attraction vers le centre (0,0,0) ---
        // C'est la force mathématique qui oblige les deux nuées à converger.
        Point3D centerForce = seekCenter(Point3D.ZERO);
        
        // On combine les forces locales (flock) et globale (center)
        // Le poids (0.4) détermine l'importance de l'attraction centrale.
        Point3D totalAcceleration = flockForce.add(centerForce.multiply(0.4));
        
        // Appliquer l'accélération
        velocity = velocity.add(totalAcceleration);
        
        // Limiter la vitesse
        if (velocity.magnitude() > maxSpeed) {
            velocity = velocity.normalize().multiply(maxSpeed);
        }
        
        position = position.add(velocity);
        // Note : J'ai supprimé wrapEdges(). L'attraction centrale les garde dans la zone.
        updateViewTransform();
    }
    
    // --- Mathématiques des Forces ---

    // Nouvelle formule : Piloter vers une cible
    private Point3D seekCenter(Point3D target) {
        // Vecteur idéal vers la cible
        Point3D desired = target.subtract(position);
        double d = desired.magnitude();
        desired = desired.normalize();

        // Si on est loin, vitesse max. Si on est très près, on ralentit (optionnel, mais plus joli)
        if (d < 100) {
            desired = desired.multiply(maxSpeed * (d/100.0));
        } else {
            desired = desired.multiply(maxSpeed);
        }

        // Force de pilotage = Vitesse désirée - Vitesse actuelle
        Point3D steer = desired.subtract(velocity);
        return limitForce(steer);
    }

    private Point3D calculateFlockingForces(List<Boid> boids) {
        Point3D separation = Point3D.ZERO;
        Point3D alignment = Point3D.ZERO;
        Point3D cohesion = Point3D.ZERO;
        int total = 0;

        for (Boid other : boids) {
            double distance = position.distance(other.position);
            if (other != this && distance < perceptionRadius) {
                // Séparation : Poids inversé à la distance
                separation = separation.add(position.subtract(other.position).normalize().multiply(1.0/distance));
                alignment = alignment.add(other.velocity);
                cohesion = cohesion.add(other.position);
                total++;
            }
        }

        if (total > 0) {
            alignment = limitForce(alignment.multiply(1.0 / total).normalize().multiply(maxSpeed).subtract(velocity));
            cohesion = limitForce(seekCenter(cohesion.multiply(1.0 / total))); // Utilise la logique seek vers le centre de masse local
            separation = limitForce(separation.multiply(1.0 / total).normalize().multiply(maxSpeed).subtract(velocity));

            // Poids des règles : Séparation est cruciale pour éviter qu'ils ne s'écrasent en un point
            return separation.multiply(1.5)
                   .add(alignment.multiply(1.0))
                   .add(cohesion.multiply(1.0));
        }
        return Point3D.ZERO;
    }
    
    private Point3D limitForce(Point3D force) {
        if (force.magnitude() > maxForce) {
            return force.normalize().multiply(maxForce);
        }
        return force;
    }

    private void updateViewTransform() {
        boidView.setTranslateX(position.getX());
        boidView.setTranslateY(position.getY());
        boidView.setTranslateZ(position.getZ());

        // Calcul de l'orientation (lacet et tangage) pour que le cône pointe dans la direction de la vitesse
        Point3D direction = velocity.normalize();
        double yaw = Math.toDegrees(Math.atan2(direction.getX(), direction.getZ()));
        double pitch = Math.toDegrees(Math.asin(-direction.getY()));

        // L'ordre des rotations est important en 3D
        boidView.setRotationAxis(Rotate.Y_AXIS);
        boidView.setRotate(yaw);
        // On applique une seconde rotation locale pour le tangage (le nez vers le haut/bas)
        // Note: une implémentation parfaite utiliserait des Quaternions, mais ceci est une approximation acceptable.
        Rotate rPitch = new Rotate(pitch, Rotate.X_AXIS);
        boidView.getTransforms().clear(); // Reset pour éviter l'accumulation
        boidView.getTransforms().addAll(new Rotate(yaw, Rotate.Y_AXIS), rPitch);
    }
    
    // Imports nécessaires pour la rotation ajoutée
    // Assurez-vous d'ajouter ceci en haut si ce n'est pas automatique :
    // import javafx.scene.transform.Rotate;

    public MeshView getView() { return boidView; }
}