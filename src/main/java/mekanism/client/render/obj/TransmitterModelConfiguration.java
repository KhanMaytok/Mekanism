package mekanism.client.render.obj;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import mekanism.client.model.data.TransmitterModelData;
import mekanism.client.model.data.TransmitterModelData.Diversion;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransmitterModelConfiguration extends VisibleModelConfiguration {

    @NotNull
    private final TransmitterModelData modelData;

    public TransmitterModelConfiguration(IGeometryBakingContext internal, List<String> visibleGroups, @NotNull ModelData modelData) {
        super(internal, visibleGroups);
        this.modelData = Objects.requireNonNull(modelData.get(TileEntityTransmitter.TRANSMITTER_PROPERTY), "Transmitter property must be present.");
    }

    @Nullable
    private static Direction directionForPiece(@NotNull String piece) {
        if (piece.endsWith("down")) {
            return Direction.DOWN;
        } else if (piece.endsWith("up")) {
            return Direction.UP;
        } else if (piece.endsWith("north")) {
            return Direction.NORTH;
        } else if (piece.endsWith("south")) {
            return Direction.SOUTH;
        } else if (piece.endsWith("east")) {
            return Direction.EAST;
        } else if (piece.endsWith("west")) {
            return Direction.WEST;
        }
        return null;
    }

    private String adjustTextureName(String name) {
        Direction direction = directionForPiece(name);
        if (direction != null) {
            if (getIconStatus(direction) != IconStatus.NO_SHOW) {
                name = name.contains("glass") ? "#side_glass" : "#side";
            }
            if (MekanismConfig.client.opaqueTransmitters.get()) {
                //If we have opaque transmitters set to true, then replace our texture with the given reference
                if (name.startsWith("#side")) {
                    return name + "_opaque";
                } else if (name.startsWith("#center")) {
                    return name.contains("glass") ? "#center_glass_opaque" : "#center_opaque";
                }
            }
            return name;
        } else if (MekanismConfig.client.opaqueTransmitters.get() && name.startsWith("#side")) {
            //If we have opaque transmitters set to true, then replace our texture with the given reference
            return name + "_opaque";
        }
        return name;
    }

    public IconStatus getIconStatus(Direction side) {
        if (modelData instanceof Diversion) {
            return IconStatus.NO_SHOW;
        }
        boolean hasConnection = modelData.getConnectionType(side) != ConnectionType.NONE;
        Predicate<Direction> has = dir -> modelData.getConnectionType(dir) != ConnectionType.NONE;
        if (!hasConnection) {
            //If we don't have a connection coming out of this side
            boolean hasUpDown = has.test(Direction.DOWN) || has.test(Direction.UP);
            boolean hasNorthSouth = has.test(Direction.NORTH) || has.test(Direction.SOUTH);
            boolean hasEastWest = has.test(Direction.EAST) || has.test(Direction.WEST);
            switch (side) {
                case DOWN, UP -> {
                    if (hasNorthSouth && !hasEastWest || !hasNorthSouth && hasEastWest) {
                        if (has.test(Direction.NORTH) && has.test(Direction.SOUTH)) {
                            return IconStatus.NO_ROTATION;
                        } else if (has.test(Direction.EAST) && has.test(Direction.WEST)) {
                            return IconStatus.ROTATE_270;
                        }
                    }
                }
                case NORTH, SOUTH -> {
                    if (hasUpDown && !hasEastWest || !hasUpDown && hasEastWest) {
                        if (has.test(Direction.UP) && has.test(Direction.DOWN)) {
                            return IconStatus.NO_ROTATION;
                        } else if (has.test(Direction.EAST) && has.test(Direction.WEST)) {
                            return IconStatus.ROTATE_270;
                        }
                    }
                }
                case WEST, EAST -> {
                    if (hasUpDown && !hasNorthSouth || !hasUpDown && hasNorthSouth) {
                        if (has.test(Direction.UP) && has.test(Direction.DOWN)) {
                            return IconStatus.NO_ROTATION;
                        } else if (has.test(Direction.NORTH) && has.test(Direction.SOUTH)) {
                            return IconStatus.ROTATE_270;
                        }
                    }
                }
            }
        }
        return IconStatus.NO_SHOW;
    }

    @Override
    public boolean hasMaterial(@NotNull String name) {
        return internal.hasMaterial(adjustTextureName(name));
    }

    @NotNull
    @Override
    public Material getMaterial(@NotNull String name) {
        return internal.getMaterial(adjustTextureName(name));
    }

    public enum IconStatus {
        NO_ROTATION(0),
        ROTATE_270(270),
        NO_SHOW(0);

        private final float angle;

        IconStatus(float angle) {
            this.angle = angle;
        }

        public float getAngle() {
            return angle;
        }
    }
}