package representation;

import java.util.Random;

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