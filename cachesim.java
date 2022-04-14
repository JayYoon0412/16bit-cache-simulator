import java.io.*;
import java.util.*;

public class cachesim {
    public static int cacheSize = 0;
    public static int assoc = 0;
    public static int blockSize = 0;
    public static int blockNum = 0;
    public static int setNum = 0;
    public static int tagSize = 0;
    public static int indexSize = 0;
    public static int offsetSize = 0;
    public static int time = 0;
    public static boolean writeBack;
    public static int addressSize = 16;

    public static cacheSet[] cache;
    public static String[] mainMemory = new String[(int) Math.pow(2, 16)];

    public static class Block {
        int validBit, dirtyBit, counter;
        String tag, data;
        public Block (String t, int vb, int db, String d, int c) {
            tag = t;
            validBit = vb;
            dirtyBit = db;
            data = d;
            counter = c;
        }
    }

    public static class cacheSet {
        ArrayList<Block> blocks;
        public cacheSet() {
            blocks = new ArrayList<Block>();
        }
    }

    public static void main(String[] args) throws IOException {
        Scanner scnr = new Scanner(new File(args[0]));
        cacheSize = (int)(Math.pow(2, 10)) * Integer.parseInt(args[1]);
        assoc = Integer.parseInt(args[2]);
        blockSize = Integer.parseInt(args[4]);
        blockNum = cacheSize/blockSize;
        setNum = blockNum/assoc;
        cache = new cacheSet[setNum];
        indexSize = (int) (Math.log(setNum)/Math.log(2));
        offsetSize = (int) (Math.log(blockSize)/Math.log(2));
        tagSize = addressSize - indexSize - offsetSize;
        if (args[3].equals("wb")) {
            writeBack = true;
        }
        else {
            writeBack = false;
        }
        setupCache();
        readInstructions(scnr);
    }

    public static void setupCache() {
        Arrays.fill(mainMemory, "00");
        for (int i=0; i<setNum; i++) {
            cacheSet set = new cacheSet();
            for (int k=0; k<assoc; k++) {
                Block block = new Block("", 0, 0, "", 0);
                set.blocks.add(block);
            }
            cache[i] = set;
        }
    }

    public static void readInstructions(Scanner scnr) throws IOException {
        while(scnr.hasNextLine()) {
            String instrn = scnr.nextLine();
            String [] arr = instrn.split(" ");
            String hexAddress = arr[1];
            String operation = arr[0];
            int accessBytes = Integer.parseInt(arr[2]);
            if (operation.equals("load")) {
                String loadResult = requestLoad(hexAddress, accessBytes);
                printResult(operation, hexAddress, loadResult);
            }
            if (operation.equals("store")) {
                String storeData = arr[3];
                String storeResult = requestStore(hexAddress, accessBytes, storeData);
                printResult(operation, hexAddress, storeResult);
            }
            time++;
        }
        scnr.close();
    }

    public static void printResult(String operation, String address, String result) {
        System.out.println(operation+" "+address+" "+result);
    }

    public static String requestLoad(String address, int bytes) {
        String missOrHit = "";
        String add = hexToBinary(address);
        while(add.length()<16) {
            add = "0"+add;
        }
        int blockOffset = Integer.parseInt(add.substring(add.length()-offsetSize), 2);
        int index = Integer.parseInt(add.substring(add.length()-offsetSize-indexSize, add.length()-offsetSize), 2);
        String tag = add.substring(0, add.length()-offsetSize-indexSize);
  
        missOrHit += loadData(add, bytes, blockOffset, index, tag);
        return missOrHit;
    }

    public static String requestStore(String address, int bytes, String storeData) {
        String missOrHit = "";
        String add = hexToBinary(address);
        while(add.length()<16) {
            add = "0"+add;
        }
        int blockOffset = Integer.parseInt(add.substring(add.length()-offsetSize), 2);
        int index = Integer.parseInt(add.substring(add.length()-offsetSize-indexSize, add.length()-offsetSize), 2);
        String tag = add.substring(0, add.length()-offsetSize-indexSize);
  
        missOrHit += storeData(add, bytes, blockOffset, index, tag, storeData);
        return missOrHit;
    }

    public static String storeData(String add, int bytes, int blockOffset, int index, String tag, String storeData) {   
        for (int i=0; i<cache[index].blocks.size(); i++) {
            if(((cache[index].blocks.get(i).tag).equals(tag))&&((cache[index].blocks.get(i).validBit)==1)) {
                writeToBlock(bytes, blockOffset, storeData, cache[index].blocks.get(i));
                if (!writeBack) {
                    writeToMemory(add, bytes, storeData);
                    return "hit";
                }
                if (writeBack) {
                    cache[index].blocks.get(i).dirtyBit = 1;
                    return "hit";
                }
            }
        }
        if (!writeBack) {
            writeToMemory(add, bytes, storeData);
            return "miss";
        }
        //if write back, bring block to cache if not in cache already
        if (writeBack) {
            int decAddress = Integer.parseInt(binaryToDecimal(add));
            int startingPoint = (decAddress/blockSize)*blockSize;
            String blockInfo = "";
            for (int k=0; k<blockSize; k++) {
                blockInfo += mainMemory[startingPoint+k];
            }
            Block wb = new Block(tag, 1, 1, blockInfo, time);
            if (cache[index].blocks.size()>=assoc) {
                int LRUindex = removeLRU(cache[index].blocks);
                if (writeBack&&cache[index].blocks.get(LRUindex).dirtyBit==1) {
                    Block lru = cache[index].blocks.get(LRUindex);
                    String addGen = generateAddress(index, lru.tag);
                    writeToMemory(addGen, blockSize, lru.data);
                }
                cache[index].blocks.remove(LRUindex);
            }
            writeToBlock(bytes, blockOffset, storeData, wb);
            cache[index].blocks.add(wb);
        }
        return "miss";
    }

    public static void writeToMemory(String add, int bytes, String storeData) {
        int decAddress = Integer.parseInt(binaryToDecimal(add));
        for (int i=0; i<bytes; i++) {
            String byteStore = storeData.substring(i*2, i*2+2);
            mainMemory[decAddress+i] = byteStore;
        }
    }
    public static void writeToBlock(int bytes, int blockOffset, String storeData, Block b) {
            String newInfo = b.data.substring(0, 2*blockOffset);
            newInfo += storeData;
            newInfo += b.data.substring(2*blockOffset+bytes*2);
            b.data = newInfo;
            b.counter = time;
            b.validBit = 1;
            b.dirtyBit = 1;
    }

    public static String loadData(String add, int bytes, int blockOffset, int index, String tag) {   
        for (int i=0; i<cache[index].blocks.size(); i++) {
            if(((cache[index].blocks.get(i).tag).equals(tag))&&((cache[index].blocks.get(i).validBit)==1)) {
                cache[index].blocks.get(i).counter = time;
                return "hit"+" "+cache[index].blocks.get(i).data.substring(blockOffset*2, 2*bytes+2*blockOffset);
            }
        }
        String newInfo = "";
        int decAddress = Integer.parseInt(binaryToDecimal(add));
        for (int j=0; j<bytes; j++) {
            newInfo += mainMemory[decAddress+j];
        }
        int startPoint = (decAddress / blockSize)*blockSize ;
        String blockInfo = "";
        for (int k=0; k<blockSize; k++) {
            blockInfo += mainMemory[startPoint+k];
        }
        Block b = new Block(tag, 1, 0, blockInfo, time);
        if (cache[index].blocks.size()>=assoc) {
            int LRUindex = removeLRU(cache[index].blocks);
            //check if this block dirty bit is 1, and if so, write to memory by recreating address from the bits we have
            if (writeBack&&cache[index].blocks.get(LRUindex).dirtyBit==1) {
                Block lru = cache[index].blocks.get(LRUindex);
                String addGen = generateAddress(index, lru.tag);
                writeToMemory(addGen, blockSize, lru.data);
            }
            cache[index].blocks.remove(LRUindex);
        }
        cache[index].blocks.add(b);
        return "miss"+" "+newInfo;
    }

    //generate binary address
    public static String generateAddress(int index, String tag) {
        String indexExtended = Integer.toBinaryString(index);
        while (indexExtended.length()<indexSize) {
            indexExtended = "0"+indexExtended;
        }
        String offsetExtended = "";
        while (offsetExtended.length()<offsetSize) {
            offsetExtended = "0"+offsetExtended;
        }
        return tag+indexExtended+offsetExtended;
    }

    public static int removeLRU(ArrayList<Block> bs) {
        int min = Integer.MAX_VALUE;
        int index = 0;
        for (int i=0; i<bs.size(); i++) {
            if (bs.get(i).counter<min) {
                min = bs.get(i).counter;
                index = i;
            }
        }
    return index;
    }

    public static String hexToBinary(String str){
		int binary = Integer.parseInt(str, 16);
		return Integer.toBinaryString(binary);
    }

	public static String binaryToDecimal(String str){
		return Integer.toString(Integer.parseInt(str, 2));
	}

}