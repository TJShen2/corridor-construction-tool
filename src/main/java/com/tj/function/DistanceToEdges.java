package com.tj.function;

import java.util.Objects;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Direction;

public record DistanceToEdges(int north, int south, int west, int east) {
  public boolean compatibleAlignment(DistanceToEdges other) {
    return north > other.north && south > other.south && west > other.west && east > other.east;
  }
  @Override
  public boolean equals(Object o) {
    if (o == this)
        return true;
    if (!(o instanceof DistanceToEdges)) {
        return false;
    }
    DistanceToEdges distanceToEdgesC = (DistanceToEdges) o;
    return north == distanceToEdgesC.north && south == distanceToEdgesC.south && east == distanceToEdgesC.east && west == distanceToEdgesC.west;
  }

  @Override
  public int hashCode() {
    return Objects.hash(north, south, east, west);
  }

  public static DistanceToEdges findEdges(EditSession editSession, Mask mask, BlockVector3 point, int horizontalSearchDistance, int verticalSearchDistance) {
    return new DistanceToEdges(
        distanceToEdge(editSession, mask, point, Direction.NORTH, horizontalSearchDistance, verticalSearchDistance),
        distanceToEdge(editSession, mask, point, Direction.SOUTH, horizontalSearchDistance, verticalSearchDistance),
        distanceToEdge(editSession, mask, point, Direction.WEST, horizontalSearchDistance, verticalSearchDistance),
        distanceToEdge(editSession, mask, point, Direction.EAST, horizontalSearchDistance, verticalSearchDistance));
  }

  public static BlockVector3 findEdge(EditSession editSession, Mask mask, BlockVector3 startPos, Direction dir, int horizontalSearchDistance, int verticalSearchDistance) {
		int highestYPos = editSession.getHighestTerrainBlock(startPos.getX(), startPos.getZ(), startPos.getY() - verticalSearchDistance, startPos.getY() + verticalSearchDistance, mask);
		if (horizontalSearchDistance == 0) {
			return null;
		} else if (highestYPos == startPos.getY() - verticalSearchDistance) {
      return startPos.subtract(dir.toBlockVector());
    } else {
      return findEdge(editSession, mask, startPos.withY(highestYPos).add(dir.toBlockVector()), dir, horizontalSearchDistance - 1, verticalSearchDistance);
    }
  }

	public static int distanceToEdge(EditSession editSession, Mask mask, BlockVector3 startPos, Direction dir, int horizontalSearchDistance, int verticalSearchDistance) {
		BlockVector3 otherPos = findEdge(editSession, mask, startPos.add(dir.toBlockVector()), dir, horizontalSearchDistance, verticalSearchDistance);
		return otherPos == null ? -1 : (int) startPos.toBlockVector2().distance(otherPos.toBlockVector2());
  }
}
