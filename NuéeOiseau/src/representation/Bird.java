package representation;

import java.util.List;
import java.util.Random;

class Bird {
    Vector3D pos, vel, acc;
    
    // États
    boolean isLeader = false;
    boolean isTired = false;
    boolean isAlerted = false;
    
    double maxSpeed = 5.5;
    double maxForce = 0.15;
    
    double stamina = 100;
    double timeSpentLeading = 0;
    
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
    
    // Accumulation des forces (Newton: F = m*a, ici m=1)
    // Acc = Acc + Force
    
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
            
            // Pré-filtrage par axe (Optimisation spatiale simple)
            // |xi - xj| > perception
            if (Math.abs(other.pos.x - pos.x) > perception) continue; 
            
            // Calcul distance au carré
            // d^2 = (xi-xj)^2 + (yi-yj)^2 + (zi-zj)^2
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
                
                // Calcul des voisins devant (aspiration)
                // V . U = |V||U|cos(theta)
                // Si cos(theta) > 0.3, alors l'angle est faible (devant)
                Vector3D toOther = Vector3D.sub(other.pos, pos);
                toOther.normalize();
                if (myHeading.dot(toOther) > 0.3) neighborsInFront++;
                neighborsCount++;
            }
        }
        
        // Définition du leader
        boolean isGeometricLeader = (neighborsCount > 2 && neighborsInFront == 0);
        
        if (isGeometricLeader && !isTired) {
            isLeader = true;
            // Incrémentation fatigue du leader
            // Fatigue = temps * dt
            timeSpentLeading += dt;
            
            if (timeSpentLeading > 10.0) {
                isTired = true;
                stamina = 0;
                timeSpentLeading = 0;
            }
        } else {
            isLeader = false;
            timeSpentLeading = 0;
        }
        
        if (neighborsCount > 0) {
        	
            // Moyenne et Steering de Reynolds
            // Steering = (Desired - Velocity)
            
            // Alignement: Desired = Moyenne(Velocity)
            ali.div(neighborsCount); ali.setMag(maxSpeed); ali.sub(vel); ali.limit(maxForce);
            // Cohésion: Target = Moyenne(Pos), Desired = Target - Pos
            coh.div(neighborsCount); coh.sub(pos); coh.setMag(maxSpeed); coh.sub(vel); coh.limit(maxForce);
            // Séparation: Moyenne des vecteurs de répulsion
            sep.div(neighborsCount); sep.setMag(maxSpeed); sep.sub(vel); sep.limit(maxForce * 2.5);
        }
        
        // Détection du prédateur
        this.isAlerted = false;
        if (pred.state == Predator.State.HUNTING) {
            double distPredSq = Vector3D.distSq(pos, pred.pos);
            if (distPredSq < 130 * 130) isAlerted = true;
            if (pred.isRoaring && distPredSq < pred.roarRadius * pred.roarRadius) isAlerted = true;

            if (this.isAlerted) {
                // Force de fuite (Opposée au prédateur)
                // F = Normalize(Pos - PredPos) * MaxSpeed
                Vector3D flee = Vector3D.sub(pos, pred.pos);
                flee.normalize();
                flee.mult(6.0); 
                applyForce(flee);
            }
        }

        
        // Gestion de la stamina
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
        
        
        // Pondération des forces
        // F_tot = 2.8*Sep + 1.0*Ali + 0.8*Coh ...
        sep.mult(2.8); ali.mult(1.0); coh.mult(0.8);
        applyForce(sep); applyForce(ali); applyForce(coh);
        applyForce(wind); applyForce(foodForce); applyForce(wallForce);
    }

    public void update(double dt, double boxSize) {
        // V(t+1) = V(t) + Acc * dt
    	vel.add(acc);
        
        double currentMax = maxSpeed;
        double currentMin = 3.5;
        
        // Gestion de la vitesse par état
        if (isTired) {
            currentMax = maxSpeed * 0.5; 
            currentMin = 1.0;
        } else if (isAlerted) {
            currentMax = maxSpeed * 1.5; 
        }

        double speed = vel.mag();
        if (speed < 0.0001) speed = 0.0001;
        
        // Clamping de la vitesse
        // V = min(max(V, minSpeed), maxSpeed)
        if (speed > currentMax) vel.setMag(currentMax);
        else if (speed < currentMin) vel.setMag(currentMin);
        
        // P(t+1) = P(t) + V * dt
        pos.add(vel);
        acc.mult(0);
        
        double limit = boxSize/2;
        if (pos.x > limit) pos.x = limit; if (pos.x < -limit) pos.x = -limit;
        if (pos.y > limit) pos.y = limit; if (pos.y < -limit) pos.y = -limit;
        if (pos.z > limit) pos.z = limit; if (pos.z < -limit) pos.z = -limit;
    }
}