package com.example;

import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;

/**
 * This record contains the information needed to call the EditSession.setBlock method of the WorldEdit API.
 * @author TJ Shen
 * @version 1.0.0
 */
public record SetBlockOperation(BlockVector3 point, Pattern pattern) {}
