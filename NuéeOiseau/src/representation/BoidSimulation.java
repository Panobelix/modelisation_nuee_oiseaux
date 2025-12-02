package representation;// BoidSimulation.java

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BoidSimulation extends Application {
    
    private static final int NUM_BOIDS = 100;
    private static final double SCENE_SIZE = 100.0;
    private final List<Boid> boids = new ArrayList<>();
    private final Group boidGroup = new Group();
    private final Group root = new Group();
    private final Random random = new Random();

    @Override
    public void start(Stage stage) {
        // Initialisation des Boids
        for (int i = 0; i < NUM_BOIDS; i++) {
            double x = random.nextDouble() * SCENE_SIZE - SCENE_SIZE / 2.0;
            double y = random.nextDouble() * SCENE_SIZE - SCENE_SIZE / 2.0;
            double z = random.nextDouble() * SCENE_SIZE - SCENE_SIZE / 2.0;
            Boid boid = new Boid(x, y, z, null);
            boids.add(boid);
            boidGroup.getChildren().add(boid.getView());
        }

        // Configuration de la scène 3D
        root.getChildren().add(boidGroup);
        Scene scene = new Scene(root, 800, 600, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.web("#222233")); // Fond sombre

        // Configuration de la caméra
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(1000.0);
        camera.setTranslateZ(-300); // Reculer la caméra
        camera.setTranslateY(50); // Léger angle
        root.getChildren().add(camera);
        scene.setCamera(camera);

        // Moteur de la simulation (AnimationTimer)
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Pour chaque Boid, calculer la nouvelle position
                for (Boid boid : boids) {
                    boid.update(boids);
                }
            }
        }.start();

        // Contrôles de la caméra (simple rotation)
        setupCameraControls(scene, root, camera);

        stage.setTitle("Boids 3D Simulation");
        stage.setScene(scene);
        stage.show();
    }

    private void setupCameraControls(Scene scene, Group root, PerspectiveCamera camera) {
        // Exemple simple : rotation de la caméra avec la souris
        Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        root.getTransforms().addAll(rotateX, rotateY);
        
        // Mouvement de la souris
        scene.setOnMousePressed(event -> {
            // ... (logique pour stocker les coordonnées initiales)
        });
        
        scene.setOnMouseDragged(event -> {
            // Une implémentation complète des contrôles de caméra est longue,
            // mais l'idée est de modifier les angles de 'rotateX' et 'rotateY' ici.
            // Par souci de brièveté, je n'inclus pas l'implémentation complète des contrôles de caméra.
        });
        
        // Zoom avec la molette
        scene.setOnScroll(event -> {
            camera.setTranslateZ(camera.getTranslateZ() + event.getDeltaY() * 0.5);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}