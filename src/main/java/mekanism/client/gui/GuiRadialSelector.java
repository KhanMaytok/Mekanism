package mekanism.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Matrix4f;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mekanism.api.IDisableableEnum;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.item.interfaces.IRadialSelectorEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.NotNull;

public class GuiRadialSelector<TYPE extends Enum<TYPE> & IRadialSelectorEnum<TYPE>> extends Screen {

    private static final float DRAWS = 300;

    private static final float INNER = 40, OUTER = 100;
    private static final float SELECT_RADIUS = 10;

    private final Class<TYPE> enumClass;
    private final TYPE[] types;
    private final Supplier<TYPE> curSupplier;
    private final Consumer<TYPE> changeHandler;
    private final boolean isDisableable;

    private TYPE selection = null;

    public GuiRadialSelector(Class<TYPE> enumClass, Supplier<TYPE> curSupplier, Consumer<TYPE> changeHandler) {
        super(MekanismLang.RADIAL_SCREEN.translate());
        this.enumClass = enumClass;
        this.curSupplier = curSupplier;
        this.changeHandler = changeHandler;
        isDisableable = IDisableableEnum.class.isAssignableFrom(enumClass);
        types = enumClass.getEnumConstants();
    }

    @Override
    public void render(@NotNull PoseStack matrix, int mouseX, int mouseY, float partialTick) {
        // center of screen
        float centerX = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2F;
        float centerY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2F;

        matrix.pushPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        matrix.translate(centerX, centerY, 0);
        RenderSystem.disableTexture();

        // Calculate number of available modes to switch between
        int activeModes;
        if (isDisableable) {
            activeModes = (int) Arrays.stream(types).filter(type -> ((IDisableableEnum<?>) type).isEnabled()).count();
        } else {
            activeModes = types.length;
        }

        // draw base
        RenderSystem.setShaderColor(0.3F, 0.3F, 0.3F, 0.5F);
        drawTorus(matrix, 0, 360);

        TYPE cur = curSupplier.get();
        // Draw segments
        if (cur != null) {
            // draw current selected
            if (cur.getColor() == null) {
                RenderSystem.setShaderColor(0.4F, 0.4F, 0.4F, 0.7F);
            } else {
                MekanismRenderer.color(cur.getColor(), 0.3F);
            }
            int section;
            if (isDisableable) {
                //Calculate the proper section to highlight as green in case one of the earlier ones is disabled
                section = 0;
                for (TYPE type : types) {
                    if (((IDisableableEnum<?>) type).isEnabled()) {
                        if (type == cur) {
                            break;
                        }
                        section++;
                    }
                }
            } else {
                section = cur.ordinal();
            }
            drawTorus(matrix, -90F + 360F * (-0.5F + section) / activeModes, 360F / activeModes);

            double xDiff = mouseX - centerX;
            double yDiff = mouseY - centerY;
            if (Math.sqrt(xDiff * xDiff + yDiff * yDiff) >= SELECT_RADIUS) {
                // draw mouse selection highlight
                float angle = (float) Math.toDegrees(Math.atan2(yDiff, xDiff));
                RenderSystem.setShaderColor(0.8F, 0.8F, 0.8F, 0.3F);
                drawTorus(matrix, 360F * (-0.5F / activeModes) + angle, 360F / activeModes);

                float selectionAngle = angle + 90F + (360F * (0.5F / activeModes));
                while (selectionAngle < 0) {
                    selectionAngle += 360F;
                }
                int selectionDrawnPos = (int) (selectionAngle * (activeModes / 360F));
                if (isDisableable) {
                    int count = 0;
                    for (TYPE type : types) {
                        if (((IDisableableEnum<?>) type).isEnabled()) {
                            if (count == selectionDrawnPos) {
                                selection = type;
                                break;
                            }
                            count++;
                        }
                    }
                } else {
                    selection = types[selectionDrawnPos];
                }

                // draw hovered selection
                RenderSystem.setShaderColor(0.6F, 0.6F, 0.6F, 0.7F);
                drawTorus(matrix, -90F + 360F * (-0.5F + selectionDrawnPos) / activeModes, 360F / activeModes);
            } else {
                selection = null;
            }
        }

        MekanismRenderer.resetColor();

        // Icons & Labels
        RenderSystem.enableTexture();
        int position = 0;
        for (TYPE type : types) {
            if (isDisableable && !((IDisableableEnum<?>) type).isEnabled()) {
                // Mode disabled, skip it.
                continue;
            }

            double angle = Math.toRadians(270 + 360 * ((float) position / activeModes));
            float x = (float) Math.cos(angle) * (INNER + OUTER) / 2F;
            float y = (float) Math.sin(angle) * (INNER + OUTER) / 2F;
            // draw icon, note: shader is set by blit
            RenderSystem.setShaderTexture(0, type.getIcon());
            blit(matrix, Math.round(x - 12), Math.round(y - 20), 24, 24, 0, 0, 18, 18, 18, 18);
            // draw label
            matrix.pushPose();
            int width = font.width(type.getShortText());
            matrix.translate(x, y, 0);
            matrix.scale(0.6F, 0.6F, 0.6F);
            font.draw(matrix, type.getShortText(), -width / 2F, 8, 0xCCFFFFFF);
            matrix.popPose();
            position++;
        }

        MekanismRenderer.resetColor();
        matrix.popPose();
    }

    @Override
    public void removed() {
        updateSelection();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // handle & ignore all key events
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateSelection();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawTorus(PoseStack matrix, float startAngle, float sizeAngle) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferBuilder vertexBuffer = Tesselator.getInstance().getBuilder();
        Matrix4f matrix4f = matrix.last().pose();
        vertexBuffer.begin(Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION);
        float draws = DRAWS * (sizeAngle / 360F);
        for (int i = 0; i <= draws; i++) {
            float angle = (float) Math.toRadians(startAngle + (i / DRAWS) * 360);
            vertexBuffer.vertex(matrix4f, (float) (OUTER * Math.cos(angle)), (float) (OUTER * Math.sin(angle)), 0).endVertex();
            vertexBuffer.vertex(matrix4f, (float) (INNER * Math.cos(angle)), (float) (INNER * Math.sin(angle)), 0).endVertex();
        }
        BufferUploader.drawWithShader(vertexBuffer.end());
    }

    public void updateSelection() {
        if (selection != null) {
            changeHandler.accept(selection);
        }
    }

    public Class<TYPE> getEnumClass() {
        return enumClass;
    }
}
