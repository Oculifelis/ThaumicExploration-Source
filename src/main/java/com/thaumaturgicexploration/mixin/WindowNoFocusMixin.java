package com.thaumaturgicexploration.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test clients only (-Dthaumaturgicexploration.autotest set): the game window opens without taking focus or jumping to the
 * front, so a muted test run never interrupts whoever is at the PC. Normal play is untouched.
 */
@Mixin(Window.class)
public class WindowNoFocusMixin {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private static void thaumaturgicexploration$noFocus(CallbackInfoReturnable<Long> cir) {
        if (System.getProperty("thaumaturgicexploration.autotest") != null) {
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }
}
