package com.tj.corridorconstructiontool.operation;

import java.util.LinkedList;
import java.util.List;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;

public record ColumnOperation(EditSession session, BlockVector2 column, int maxY, Mask replaceableBlockMask, Pattern pattern) implements Operation {
  @Override
  public boolean complete() {
    for (int i = maxY; i > session.getWorld().getMinY(); i--) {
			BlockVector3 point = column.toBlockVector3(i);
			if (replaceableBlockMask.test(point)) {
				try {
					session.setBlock(point, pattern);
				} catch (MaxChangedBlocksException e) {
					return false;
				}
			} else {
				return true;
			}
		}
    return true;
  }

	@Override
  public List<SetBlockOperation> toSetBlockOperations() {
		List<SetBlockOperation> ops = new LinkedList<>();

    for (int i = maxY; i > session.getWorld().getMinY(); i--) {
			BlockVector3 point = column.toBlockVector3(i);
			if (replaceableBlockMask.test(point)) {
				ops.add(new SetBlockOperation(session, point, pattern));
			} else {
				return ops;
			}
		}
		return ops;
  }
}
