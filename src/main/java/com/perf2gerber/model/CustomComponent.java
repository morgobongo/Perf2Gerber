package com.perf2gerber.model;

import java.util.ArrayList;
import java.util.List;

public class CustomComponent extends Component {
    
    public static class ShapeDef {
        public double offsetX;
        public double offsetY;
        public double width;
        public double height;
        public String type; // "RECTANGLE", "CIRCLE", "SEMICIRCLE_TOP", etc.
        public String color;
        
        public ShapeDef() {}
        public ShapeDef(double x, double y, double w, double h) {
            this.offsetX = x; this.offsetY = y; this.width = w; this.height = h;
            this.type = "RECTANGLE";
        }
        public ShapeDef(double x, double y, double w, double h, String type, String color) {
            this.offsetX = x; this.offsetY = y; this.width = w; this.height = h;
            this.type = type;
            this.color = color;
        }
    }

    private List<ShapeDef> shapes = new ArrayList<>();

    public CustomComponent() {}

    public List<ShapeDef> getShapes() { return shapes; }
    public void setShapes(List<ShapeDef> shapes) { this.shapes = shapes; }

    @Override
    public Component cloneComponent() {
        CustomComponent clone = new CustomComponent();
        clone.setName(this.getName());
        clone.setValue(this.getValue());
        clone.setStartX(this.getStartX());
        clone.setStartY(this.getStartY());
        clone.setRotation(this.getRotation());
        clone.setShowName(this.isShowName());
        clone.setShowValue(this.isShowValue());
        clone.setType(this.getType());
        clone.setColor(this.getColor());
        
        for(ShapeDef s : this.shapes) {
            clone.getShapes().add(new ShapeDef(s.offsetX, s.offsetY, s.width, s.height, s.type, s.color));
        }
        return clone;
    }

    @Override
    public boolean contains(double worldX, double worldY) {
        double dx = worldX - getStartX();
        double dy = worldY - getStartY();
        double vx = dx;
        double vy = -dy;
        
        double rad = Math.toRadians(-getRotation());
        double rx = vx * Math.cos(rad) - vy * Math.sin(rad);
        double ry = vx * Math.sin(rad) + vy * Math.cos(rad);
        
        for (ShapeDef s : shapes) {
            if (rx >= s.offsetX && rx <= s.offsetX + s.width &&
                ry >= s.offsetY && ry <= s.offsetY + s.height) {
                return true;
            }
        }
        return false;
    }
}
