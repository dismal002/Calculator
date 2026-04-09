package com.dismal.calculator.math;

public class Point {
    private double x;
    private double y;

    public Point(double d2, double d3) {
        this.x = d2;
        this.y = d3;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public void setX(double d2) {
        this.x = d2;
    }

    public void setY(double d2) {
        this.y = d2;
    }
}
