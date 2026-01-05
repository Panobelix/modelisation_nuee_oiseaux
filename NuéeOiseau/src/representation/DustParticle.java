package representation;

import java.util.Random;

class DustParticle {
    Vector3D pos;
    Random r = new Random();
    
    public DustParticle(double boxSize) {
        respawn(boxSize);
    }
    
    public void respawn(double boxSize) {
        // Position aléatoire uniforme
        // x = (random[0,1] * 2 - 1) * (taille / 2)
        pos = new Vector3D(
            (r.nextDouble()*2-1) * boxSize/2,
            (r.nextDouble()*2-1) * boxSize/2,
            (r.nextDouble()*2-1) * boxSize/2
        );
    }
    
    public void update(WindField wind, double dt, double boxSize) {
        Vector3D w = wind.getForce(pos);
        w.mult(30.0);
        
        // Euler Integration (Pos = Pos + Vitesse)
        // P(t+1) = P(t) + V * dt (ici simplifié sans dt explicite pour l'effet)
        pos.add(w);
        
        if (Math.abs(pos.x) > boxSize/2) pos.x *= -0.95;
        if (Math.abs(pos.y) > boxSize/2) pos.y *= -0.95;
        if (Math.abs(pos.z) > boxSize/2) pos.z *= -0.95;
    }
}