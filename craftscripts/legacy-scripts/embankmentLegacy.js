importPackage(Packages.java.math);
importPackage(Packages.java.lang);
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
let groundMask = new MaskIntersection(Masks.negate(replaceableBlockMask), Masks.negate(trackMask));

//Provide feedback to user
let blocksChanged = 0;
context.print("Creating embankment...");

//Loop through all blocks in selection
let iterator = selectedRegion.iterator();

while (iterator.hasNext()) {
    let point = iterator.next();

    if (trackMask.test(point)) {
        let edgeDirection = [
            +!trackMask.test(point.add(BlockVector3.UNIT_X)),
            +!trackMask.test(point.add(BlockVector3.UNIT_Z)),
            +!trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)),
            +!trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z))
        ];
        let isSurrounded = trackMask.test(point.add(BlockVector3.UNIT_X)) && trackMask.test(point.add(BlockVector3.UNIT_Z)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z))
        let width = maxHeight / grade;
        let slopeRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width * edgeDirection[2], 0, width * edgeDirection[3]), point.add(width * edgeDirection[0], 0, width * edgeDirection[1]));
        let fullSlopeRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width, 0, width), point.add(width, 0, width));

        if (isSurrounded && point.getY() - checkHeightMap(point.toBlockVector2(), width, fullSlopeRegion, groundMask) <= maxHeight) {
            createColumn(point);
        } else if (point.getY() - checkHeightMap(point.toBlockVector2(), width, fullSlopeRegion, groundMask) <= maxHeight) {
            context.print(slopeRegion.getVolume());
            createSlope(point, slopeRegion, grade);
        }
    }
}

context.print("Embankment successfully created.");
context.print(new String(blocksChanged).concat(" blocks were changed."));

function checkHeightMap(origin2D, radius, cuboidRegion, groundMask) {
    let iterator = cuboidRegion.iterator();
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
    minimumHeightMap = Math.min.apply(null, heightMap);
    
    return minimumHeightMap;
}

function createColumn(origin) {
    let i = 0;
    if (groundMask.test(origin.subtract(0, maxHeight, 0))) {
        while (i < maxHeight) {
            let point = origin.subtract(0,i,0);
            if (replaceableBlockMask.test(point)) {
                editSession.setBlock(point, BlockTypes.get(argv[3]).getDefaultState());
                blocksChanged++;
            }
            i++;
        }
    }
}

function createSlope(origin, slopeRegion, grade) {
    let origin2D = origin.toBlockVector2();
    let iterator = slopeRegion.iterator();

    while (iterator.hasNext()) {
        let column = iterator.next().toBlockVector2();
        let distanceFromOrigin = origin2D.distance(column);
        let columnOrigin = column.toBlockVector3(origin.getY() - Math.max(1, Math.round(grade * distanceFromOrigin)));
        createColumn(columnOrigin);
    }
}