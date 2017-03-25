package com.skydragon.gplay.runtime.entity.game;


public final class VerifyInfo {
    /**
     * 兼容
     */
    private int compatible = 1;
    /**
     * 可见
     */
    private int visible = 1;
    /**
     * 维护
     */
    private int maintaining = 0;

    public boolean isCompatible() {
        return compatible == 1;
    }

    public void setCompatible(int compatible) {
        this.compatible = compatible;
    }

    public boolean isVisible() {
        return visible == 1;
    }

    public void setVisible(int visible) {
        this.visible = visible;
    }

    public boolean isMaintaining() {
        return maintaining != 1;
    }

    public void setMaintaining(int maintaining) {
        this.maintaining = maintaining;
    }
}
