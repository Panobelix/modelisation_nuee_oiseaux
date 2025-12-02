/**
 * 
 */
/**
 * 
 */
module NuéeOiseau { // Le nom de votre module
    // Permet à JavaFX de démarrer votre application
    requires javafx.controls;
    requires javafx.graphics;

    // --- Ligne CRUCIALE ---
    // Elle permet à la classe d'initialisation de JavaFX (dans javafx.graphics)
    // d'accéder au package contenant BoidSimulation.
    opens representation to javafx.graphics;
    
    // (Optionnel) Pour un lancement normal ou si vous exportez en JAR
    exports representation; 
}