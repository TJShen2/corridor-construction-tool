package com.example;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;

public class Functions {
  public static final BiFunction<BlockVector3, Mask, Integer> countAdjacentBlocks = (point, mask) -> ((mask.test(point.add(1,0,0)) || mask.test(point.add(1,1,0)) || mask.test(point.add(1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,1)) || mask.test(point.add(0,1,1)) || mask.test(point.add(0,-1,1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,0)) || mask.test(point.add(-1,1,0)) || mask.test(point.add(-1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,-1)) || mask.test(point.add(0,1,-1)) || mask.test(point.add(0,-1,-1))) ? 1 : 0) +
			((mask.test(point.add(1,0,1)) || mask.test(point.add(1,1,1)) || mask.test(point.add(1,-1,1))) ? 1 : 0) +
			((mask.test(point.add(1,0,-1)) || mask.test(point.add(1,1,-1)) || mask.test(point.add(1,-1,-1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,-1)) || mask.test(point.add(-1,1,-1)) || mask.test(point.add(-1,-1,-1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,1)) || mask.test(point.add(-1,1,1)) || mask.test(point.add(-1,-1,1))) ? 1 : 0);
  public static final BiPredicate<BlockVector3, Mask> occludedByMask2D = (point, mask) -> Functions.countAdjacentBlocks.apply(point, mask) == 8;

	public static final BiFunction<BlockVector3, Mask, Integer> countAdjacentBlocksCardinal = (point, mask) -> ((mask.test(point.add(1,0,0)) || mask.test(point.add(1,1,0)) || mask.test(point.add(1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,1)) || mask.test(point.add(0,1,1)) || mask.test(point.add(0,-1,1))) ? 1 : 0) +
			((mask.test(point.add(-1,0,0)) || mask.test(point.add(-1,1,0)) || mask.test(point.add(-1,-1,0))) ? 1 : 0) +
			((mask.test(point.add(0,0,-1)) || mask.test(point.add(0,1,-1)) || mask.test(point.add(0,-1,-1))) ? 1 : 0);
	public static final BiPredicate<BlockVector3, Mask> occludedByMaskCardinal = (point, mask) -> Functions.countAdjacentBlocksCardinal.apply(point, mask) == 4;
}
