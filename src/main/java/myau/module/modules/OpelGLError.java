package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.util.ChatUtil;
import myau.util.TimerUtil;

import java.util.Random;

public class OpelGLError extends Module {
    private final TimerUtil timer = new TimerUtil();
    private final Random random = new Random();
    public final FloatProperty delay = new FloatProperty("delay", 2.0F, 0.1F, 60.0F);

    public OpelGLError() {
        super("OpelGLError", false);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            if (this.timer.hasTimeElapsed((long) (this.delay.getValue() * 1000.0F))) {
                this.timer.reset();
                int code = 1000 + random.nextInt(9000);
                ChatUtil.sendRaw("\u00a7eOpenGL\u9519\u8bef:\u00a7f" + code);
            }
        }
    }
}
