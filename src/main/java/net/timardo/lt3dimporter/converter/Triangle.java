package net.timardo.lt3dimporter.converter;

import de.javagl.obj.FloatTuple;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Triangle {

    public Vec3d a;
    public Vec3d b;
    public Vec3d c;
    public double[] u; // [a, b, c]
    public double[] v; // ...
    //public double[] w; not currently implemented until I find an obj with a 3d texture
    public boolean t; // whether the Triangle has texture coords for its points
    
    public Triangle(FloatTuple a, FloatTuple b, FloatTuple c) {
        this.a = new Vec3d(a.getX(), a.getY(), a.getZ());
        this.b = new Vec3d(b.getX(), b.getY(), b.getZ());
        this.c = new Vec3d(c.getX(), c.getY(), c.getZ());
        this.t = false;
    }

    public void calcBlocks(float minPrecision, double scale, ConvertedModel output, String material) {
        Vec3d sA = a.scale(scale); // scale BEFORE processing
        Vec3d sB = b.scale(scale);
        Vec3d sC = c.scale(scale);
        Vec3d vectAC = sA.subtractReverse(sC); // make vectors
        Vec3d vectBC = sB.subtractReverse(sC);
        double slices = vectAC.lengthVector() / minPrecision + 2d;
        
        for (int i = 0; i <= slices; i++) {
            double t = i / slices; // ratio
            Vec3d p1 = sA.add(vectAC.scale(t));
            double[] uv1 = this.t ? new double[] { u[0] + (u[2] - u[0]) * t, v[0] + (v[2] - v[0]) * t } : null; // uv mapping for p1
            Vec3d p2 = sB.add(vectBC.scale(t));
            double[] uv2 = this.t ? new double[] { u[1] + (u[2] - u[1]) * t, v[1] + (v[2] - v[1]) * t } : null; // uv mapping for p2
            /*output.addTile(new BlockPos(p1), this.t ? new double[] { uv1[0] + (uv2[0] - uv1[0]), uv1[1] + (uv2[1] - uv1[1]) } : null, material);
            double r = 0D;
            
            for (int k = MathHelper.ceil(p1.x); k < MathHelper.ceil(p2.x); k++) {
                r = ((double) k - p1.x) / (p2.x - p1.x);
                try {
                output.addTile(new BlockPos(getIntermediate(p1, p2, (double) k, 0)), this.t ? new double[] { uv1[0] + (uv2[0] - uv1[0]) * r, uv1[1] + (uv2[1] - uv1[1]) * r } : null, material);
                } catch (NullPointerException e) { System.out.println(p1.toString() + "            " + p2.toString()); }
            }
            
            for (int k = MathHelper.ceil(p1.y); k < MathHelper.ceil(p2.y); k++) {
                r = ((double) k - p1.y) / (p2.y - p1.y);
                try {
                output.addTile(new BlockPos(getIntermediate(p1, p2, (double) k, 1)), this.t ? new double[] { uv1[0] + (uv2[0] - uv1[0]) * r, uv1[1] + (uv2[1] - uv1[1]) * r } : null, material);
                } catch (NullPointerException e) { System.out.println(p1.toString() + "            " + p2.toString()); }
            }
            
            for (int k = MathHelper.ceil(p1.z); k < MathHelper.ceil(p2.z); k++) {
                r = ((double) k - p1.z) / (p2.z - p1.z);
                try {
                output.addTile(new BlockPos(getIntermediate(p1, p2, (double) k, 2)), this.t ? new double[] { uv1[0] + (uv2[0] - uv1[0]) * r, uv1[1] + (uv2[1] - uv1[1]) * r } : null, material);
                } catch (NullPointerException e) { System.out.println(p1.toString() + "            " + p2.toString()); }
            }*/
            Vec3d v = new Vec3d(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z); // vector
            double subSlices = v.lengthVector() / minPrecision + 2d; // number of slices in new line
            
            for (int j = 0; j <= subSlices; j++) {
                double u = j / subSlices;
                output.addTile(new BlockPos(p1.add(v.scale(u))), this.t ? new double[] { uv1[0] + (uv2[0] - uv1[0]) * u, uv1[1] + (uv2[1] - uv1[1]) * u } : null, material);
            }
        }
    }
    
    public void addTexCoords(FloatTuple a, FloatTuple b, FloatTuple c) {
        this.u = new double[] { a.getX(), b.getX(), c.getX() };
        this.v = new double[] { a.getY(), b.getY(), c.getY() };
        //this.w = new double[] { a.getZ(), b.getZ(), c.getZ() };
        this.t = true;
    }
    
    /**
     * Tweaked version of a method in {@link Vec3d} TODO make this actually working with commented code above
     * 
     * @param p1 - first point (start of the vector)
     * @param p2 - seconds point
     * @param n - value to be scaled the vector to
     * @param p - 0 for scaling X, 1 for Y and 2 for Z
     * 
     * @return new {@link Vec3d} of a point between p1 and p2 scaled with chosen coordinate to n
     */
    private Vec3d getIntermediate(Vec3d p1, Vec3d p2, double n, int p) {
        double dX = p2.x - p1.x;
        double dY = p2.y - p1.y;
        double dZ = p2.z - p1.z;

        if ((p == 0 && dX == 0) || (p == 1 && dY == 0) || (p == 2 && dZ == 0)) {
            return p1; // distance is 0 and there is no need to calculate another point
        } else {
            double d3 = (n - p1.y) / (p == 0 ? dX : (p == 1 ? dY : dZ));
            return d3 >= 0.0D && d3 <= 1.0D ? new Vec3d(p1.x + dX * d3, p1.y + dY * d3, p1.z + dZ * d3) : null; // null should never happen and if it does, it's a bug
        }
    }
}
