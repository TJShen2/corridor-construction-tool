importPackage(Packages.java.math);
importPackage(Packages.com.sk89q.worldedit);
importPackage(Packages.com.sk89q.worldedit.math);
importPackage(Packages.com.sk89q.worldedit.session);
importPackage(Packages.com.sk89q.worldedit.regions);
importPackage(Packages.com.sk89q.worldedit.world.block);
importPackage(Packages.com.sk89q.worldedit.extension.input);
importPackage(Packages.com.sk89q.worldedit.extent.world);
importPackage(Packages.com.sk89q.worldedit.function.mask);

//argv:
//1: material of track bed
//2: blocks that may be replaced by the embankment
//3: material of embankment
//4: maximum height of embankment
var maxHeight = argv[4];
//5: grade of embankment
var grade = argv[5];

var editSession;
var localSession;
var selectedRegion;
var parserContext;

editSession = context.remember();

//Get the player's region selection
localSession = context.getSession();
selectedRegion = localSession.getSelection();

//Initialise parserContext
parserContext = new ParserContext();
parserContext.setExtent(new SideEffectExtent(editSession.getWorld()));
parserContext.setSession(localSession);
parserContext.setWorld(editSession.getWorld());
parserContext.setActor(player);

//Material of the track
let trackMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[1], parserContext);

//Set the mask for the blocks that may be replaced
let replaceableBlockMask = WorldEdit.getInstance().getMaskFactory().parseFromInput(argv[2], parserContext);
editSession.setMask(replaceableBlockMask);
let groundMask = Masks.negate(replaceableBlockMask);

//Loop through all blocks in selection
let iterator = selectedRegion.iterator();

while (iterator.hasNext()) {
    let point = iterator.next();

    if (trackMask.test(point)) {
        let width = maxHeight / grade;
        var slopeRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width, 0, width), point.add(width, 0, width));

        if (point.getY() - getAverageHeightMap(point.toBlockVector2(), width, slopeRegion, groundMask) <= maxHeight) {
            let isOnEdge = !(trackMask.test(point.add(BlockVector3.UNIT_X)) && trackMask.test(point.add(BlockVector3.UNIT_Z)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z)));
            if (isOnEdge) {
                createSlope(point, slopeRegion, grade);
            } else {
                createColumn(point);
            }
        }
    }
}

function getAverageHeightMap(origin2D, radius, cuboidRegion, groundMask) {
    let iterator = cuboidRegion.iterator();
    let averageHeightMap;
    const heightMap = [];

    while (iterator.hasNext()) {
        let column = iterator.next().toBlockVector2();
        
        if (column.distance(origin2D) <= radius) {
            let found = false;
            let groundY = 0;

            for (y = 320; y >= -64; y--) {
                test = column.toBlockVector3(y);
                if (!found) {
                    if (groundMask.test(column.toBlockVector3(y))) {
                        found = true;
                        groundY = y;
                    }
                }
                if (found) {
                    break;
                }
            }
            heightMap.push(y);
        } else {
            continue;
        }
    }

    let sum = 0;
    for (i = 0; i < heightMap.length; i++) {
        sum += heightMap[i];
    }
    averageHeightMap = sum / heightMap.length;

    return averageHeightMap;
}

function createColumn(origin) {
    let i = 0;
    while (true) {
        i++;
        let point = origin.subtract(0,i,0);
        if (replaceableBlockMask.test(point)) {
            editSession.setBlock(point, BlockTypes.get(argv[3]).getDefaultState());
        } else {
            break;
        }
    }
}

function createSlope(origin, slopeRegion, grade) {
    let origin2D = origin.toBlockVector2();
    let iterator = slopeRegion.iterator();

    while (iterator.hasNext()) {
        let column = iterator.next().toBlockVector2();
        let distanceFromOrigin = origin2D.distance(column);
        let columnOrigin = column.toBlockVector3(origin.getY() - Math.round(grade * distanceFromOrigin));
        createColumn(columnOrigin);
    } 
}