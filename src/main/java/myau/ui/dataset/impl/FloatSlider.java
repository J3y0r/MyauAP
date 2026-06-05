package myau.ui.dataset.impl;

import myau.enums.ChatColors;
import myau.property.properties.FloatProperty;
import myau.ui.dataset.Slider;

public class FloatSlider extends Slider {
    private final FloatProperty property;

    public FloatSlider(FloatProperty property) {
        this.property = property;
    }

    @Override
    public double getInput() {
        return property.getValue();
    }

    @Override
    public double getMin() {
        return property.getMinimum();
    }

    @Override
    public double getMax() {
        return property.getMaximum();
    }

    @Override
    public void setValue(double value) {
        int precision = detectPrecision();
        double scale = Math.pow(10, precision);
        double rounded = Math.round(value * scale) / scale;
        property.setValue((float) rounded);
    }

    @Override
    public void setValueString(String value) {
        try {
            property.setValue(Float.parseFloat(value));
        } catch (Exception ignore) {
        }
    }

    @Override
    public String getName() {
        return property.getName().replace("-", " ");
    }

    @Override
    public String getValueString() {
        return property.getValue().toString();
    }

    @Override
    public String getValueColorString() {
        return ChatColors.formatColor(property.formatValue());
    }

    @Override
    public double getIncrement() {
        return 0.1;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }

    @Override
    public int getPrecision() {
        return detectPrecision();
    }

    private int detectPrecision() {
        float rangeDiff = property.getMaximum() - property.getMinimum();
        int rangePrecision = rangeDiff > 0 ? getDecimalPlaces(rangeDiff) : getDecimalPlaces(property.getMinimum());
        int valuePrecision = getDecimalPlaces(property.getValue());
        return Math.max(1, Math.max(rangePrecision, valuePrecision));
    }

    private static int getDecimalPlaces(float value) {
        if (Float.isInfinite(value) || Float.isNaN(value)) return 0;
        float positive = Math.abs(value);
        float remainder = positive;
        for (int i = 0; i < 6; i++) {
            long rounded = Math.round(remainder);
            remainder = (float) (remainder * 10) - rounded * 10;
            if (Math.abs(remainder) < 0.001f) {
                return i == 0 ? 0 : i;
            }
        }
        return 1;
    }

    @Override
    public void stepping(boolean increment) {
        int precision = detectPrecision();
        double scale = Math.pow(10, precision);
        if (increment) {
            if (property.getValue() >= property.getMaximum()) return;
            double stepped = Math.round(property.getValue() * scale + 1) / scale;
            property.setValue((float) Math.min(stepped, property.getMaximum()));
        } else {
            if (property.getValue() <= property.getMinimum()) return;
            double stepped = Math.round(property.getValue() * scale - 1) / scale;
            property.setValue((float) Math.max(stepped, property.getMinimum()));
        }
    }
}
