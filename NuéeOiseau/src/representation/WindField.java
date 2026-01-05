package representation;

class WindField {
    double time = 0;
    
    public void update(double dt) {
        time += dt;
    }

    public Vector3D getForce(Vector3D pos) {
        double scale = 0.003;
        
        // Génération d'angles basés sur la position et le temps (pseudo-aléatoire fluide)
        // θy = x * scale + t * 0.5
        double angleY = pos.x * scale + time * 0.5;
        double angleX = pos.y * scale + time * 0.3;
        double angleZ = pos.z * scale + time * 0.4;

        // Calcul du vecteur force par fonctions trigonométriques
        // Wx = cos(angleX), Wy = sin(angleZ), Wz = cos(angleY)
        double wx = Math.cos(angleX);
        double wy = Math.sin(angleZ); 
        double wz = Math.cos(angleY); 

        Vector3D w = new Vector3D(wx, wy, wz);
        w.mult(0.15); 
        return w;
    }
}