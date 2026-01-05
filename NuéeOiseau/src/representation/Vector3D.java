package representation;

class Vector3D {
    public double x, y, z;

    public Vector3D(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public Vector3D copy() { return new Vector3D(x, y, z); }
    
    // Addition vectorielle
    // v = u + w  =>  (vx, vy, vz) = (ux+wx, uy+wy, uz+wz)
    public void add(Vector3D v) { x += v.x; y += v.y; z += v.z; }
    
    // Soustraction vectorielle
    // v = u - w
    public void sub(Vector3D v) { x -= v.x; y -= v.y; z -= v.z; }

    // Multiplication par un scalaire
    // v = k * u
    public void mult(double n) { x *= n; y *= n; z *= n; }
    
    // Division par un scalaire
    // v = u / k
    public void div(double n) { x /= n; y /= n; z /= n; }

    // Norme (Magnitude) euclidienne
    // |v| = sqrt(x^2 + y^2 + z^2)
    public double mag() { return Math.sqrt(x*x + y*y + z*z); }
    
    // Carré de la norme 
    // |v|^2 = x^2 + y^2 + z^2
    public double magSq() { return x*x + y*y + z*z; }

    // Normalisation (Vecteur unitaire)
    // û = v / |v|
    public void normalize() {
        double m = mag();
        if (m > 0) div(m);
    }
   
    // Limitation de la magnitude
    // Si |v| > max, alors v = (v / |v|) * max
    public void limit(double max) {
        if (magSq() > max * max) {
            normalize();
            mult(max);
        }
    }

    public void setMag(double len) {
        normalize();
        mult(len);
    }

    // Produit Scalaire (Dot Product)
    // A . B = Ax*Bx + Ay*By + Az*Bz = |A||B|cos(θ)
    public double dot(Vector3D v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public static Vector3D sub(Vector3D v1, Vector3D v2) {
        return new Vector3D(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z);
    }
    
    // Distance Euclidienne au carré
    // d^2 = (x1 - x2)^2 + (y1 - y2)^2 + (z1 - z2)^2
    public static double distSq(Vector3D v1, Vector3D v2) {
        return Math.pow(v1.x - v2.x, 2) + Math.pow(v1.y - v2.y, 2) + Math.pow(v1.z - v2.z, 2);
    }

    // Produit Vectoriel (Cross Product)
    // C = A ^ B (vecteur perpendiculaire à A et B)
    // Cx = AyBz - AzBy, Cy = AzBx - AxBz, Cz = AxBy - AyBx
    public static Vector3D cross(Vector3D v1, Vector3D v2) {
        return new Vector3D(
            v1.y * v2.z - v1.z * v2.y,
            v1.z * v2.x - v1.x * v2.z,
            v1.x * v2.y - v1.y * v2.x
        );
    }
}