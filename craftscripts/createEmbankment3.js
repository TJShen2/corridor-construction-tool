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

//Define masks
let groundMask = new MaskIntersection(Masks.negate(replaceableBlockMask), Masks.negate(trackMask));
let railMask = WorldEdit.getInstance().getMaskFactory().parseFromInput("mtr:rail", parserContext);
editSession.setMask(new MaskIntersection(replaceableBlockMask, Masks.negate(railMask)));

//Provide feedback to user
let blocksChanged = 0;
let blocksEvaluated = 0;
let regionSize = selectedRegion.getVolume();
context.print("Creating embankment...");

//Loop through all blocks in selection
let iterator = selectedRegion.iterator();

//Hash table containing each column near the track
let columns = new Map();

while (iterator.hasNext()) {
    let point = iterator.next();

    if (trackMask.test(point)) {
        let width = maxHeight / grade;
        let heightTestRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width, 0, width), point.add(width, 0, width));

        if (checkHeightMap(point, width, heightTestRegion)) {
            let isSurrounded = trackMask.test(point.add(BlockVector3.UNIT_X)) && trackMask.test(point.add(BlockVector3.UNIT_Z)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)) && trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z))

            if (isSurrounded) {
                createColumn(point);
            } else {
                let edgeDirection = [
                    +!trackMask.test(point.add(BlockVector3.UNIT_X)),
                    +!trackMask.test(point.add(BlockVector3.UNIT_Z)),
                    +!trackMask.test(point.add(BlockVector3.UNIT_MINUS_X)),
                    +!trackMask.test(point.add(BlockVector3.UNIT_MINUS_Z))
                ];
                let slopeRegion = new CuboidRegion(editSession.getWorld(), point.subtract(width * edgeDirection[2], 0, width * edgeDirection[3]), point.add(width * edgeDirection[0], 0, width * edgeDirection[1]));
                createSlope(point, slopeRegion, grade);
            }
        }
    }
    blocksEvaluated++;
    if (blocksEvaluated % 50000 == 0) {
        context.print(new String(Math.round(100 * blocksEvaluated / regionSize)).concat("% complete"));
    }
}

context.print("Embankment successfully created.");
context.print(new String(blocksChanged).concat(" blocks were changed."));

function checkHeightMap(origin, radius, cuboidRegion) {
    let origin2D = origin.toBlockVector2();
    let iterator = cuboidRegion.iterator();

    let validHeightCount = 0;
    let invalidHeightCount = 0;

    while (iterator.hasNext()) {
        let column = iterator.next().toBlockVector2();
        
        if (column.distance(origin2D) <= radius) {
            let isValidHeight = columns.get(column);

            if (isValidHeight == undefined) {
                isValidHeight = groundMask.test(column.toBlockVector3(origin.getY() - maxHeight));
                columns.set(column, isValidHeight);
            }

            if (isValidHeight) {
                validHeightCount++;
            } else {
                invalidHeightCount++;
            }
        }       
    }
    let isValidHeightMap = validHeightCount / invalidHeightCount >= 19;
    return isValidHeightMap;
}

function createColumn(origin) {
    let i = 0;
    while (i < maxHeight) {
        let point = origin.subtract(0,i,0);
        if (replaceableBlockMask.test(point)) {
            editSession.setBlock(point, BlockTypes.get(argv[3]).getDefaultState());
            blocksChanged++;
        }
        i++;
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