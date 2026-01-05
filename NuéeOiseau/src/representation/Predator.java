package representation;

import java.util.List;
import java.util.Random;

import representation.Predator.State;

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
            
            // Recherche du voisin le plus proche (Algorithme naïf O(N))
            // Min( d^2(Pred, Bird_i) )
            
            for (Bird b : birds) {
                double d = Vector3D.distSq(pos, b.pos);
                if (d < minDist) { minDist = d; closest = b; }
            }
            
            if (closest != null) {
            	
                // Algorithme de Poursuite (Steering: Seek)
                // Desired = Normalize(Target - Pos) * MaxSpeed
            	
                Vector3D desired = Vector3D.sub(closest.pos, pos);
                desired.normalize();
                desired.mult(huntSpeed);
                
                // Force = Desired - Velocity
                Vector3D steer = Vector3D.sub(desired, vel);
                steer.limit(0.4); 
                acc.add(steer);
            }
            if (!isRoaring && rand.nextDouble() < 0.005) triggerRoar();
        }
        
        acc.add(windForce);
        
        // Intégration d'Euler Semi-Implicite
        // V(t+1) = V(t) + Acc * dt
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
        	
            // Détection de collision sphérique
            // distSq < (RayonPred + RayonBird)^2 (ici approx 13^2 = 169)
        	
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