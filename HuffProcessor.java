import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Ow	en Astrachan
 *
 * Revise
 */

public class HuffProcessor {

	private class HuffNode implements Comparable<HuffNode> {
		HuffNode left;
		HuffNode right;
		int value;
		int weight;

		public HuffNode(int val, int count) {
			value = val;
			weight = count;
		}
		public HuffNode(int val, int count, HuffNode ltree, HuffNode rtree) {
			value = val;
			weight = count;
			left = ltree;
			right = rtree;
		}

		public int compareTo(HuffNode o) {
			return weight - o.weight;
		}
	}

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private boolean myDebugging = false;
	
	public HuffProcessor() {
		this(false);
	}
	
	public HuffProcessor(boolean debug) {
		myDebugging = debug;
	}

	private int[] getCounts (BitInputStream in) {
		int [] arr = new int [ALPH_SIZE+1];
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {break;}
			arr[val]++;
		}
		return arr;
	}

	private HuffNode makeTree (int [] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int i = 0; i< counts.length; i++) {
			if (counts[i] > 0) {
				pq.add(new HuffNode(i, counts[i], null, null)); // TODO: counts[i] & i have changed places
			}
		}
		pq.add(new HuffNode(PSEUDO_EOF, 1, null, null));

		while (pq.size()>1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, right.weight + left.weight, left, right); // TODO: not sure about the first index of values
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;

	}

	private void makeEncodings (HuffNode root, String s, String[] encodings) {
		//String [] encodings = new String[ALPH_SIZE+1];
		// makeEncodings(root, "", encodings);
		if (root.left == null && root.right == null) { // if leaf
			encodings[root.value] = s;
			return;
		}
		else {
			makeEncodings(root.left, s+"0", encodings);
			makeEncodings(root.right, s+"1", encodings);
			// return;
		}
	}


	private void writeTree (BitOutputStream out, HuffNode hn) { //TODO: this needs work
		
		if (hn.right != null && hn.left!= null) { // if its not a leaf, write a single bit of 0 // if its a leaf, write 1 and then the 9 bits that store the value
			// hn.value = 0;
			out.writeBits(1, 0);
			writeTree(out, hn.left);
			writeTree(out, hn.right);
		}
		else { // if it is a leaf  // TODO: this has to be resolved
			out.writeBits(1, 1);
			// String [] arr = new String[ALPH_SIZE+1];
			// makeEncodings(hn, "", arr);
			// String code = arr[hn.value];
			out.writeBits(BITS_PER_WORD+1, hn.value);
			// hn.value = hn.value + (int)Math.pow(10, 9); //TODO: cant return this
		}

		// String code = "hello";
		// out.writeBits(code.length(), Integer.parseInt(code,2));

	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = getCounts(in);
		HuffNode root = makeTree(counts);
		in.reset();
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(out, root);
		String[] encodings = new String[ALPH_SIZE+1];
		makeEncodings(root, "", encodings);

		int readPart = in.readBits(BITS_PER_WORD);

		while (readPart!=-1) {
			out.writeBits(encodings[readPart].length(), Integer.parseInt(encodings[readPart], 2));
			readPart = in.readBits(BITS_PER_WORD);
		}
		out.writeBits(encodings[PSEUDO_EOF].length(), Integer.parseInt(encodings[PSEUDO_EOF], 2));

		// in.close();
		out.close();
	}
	private HuffNode readTree(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) throw new HuffException("Invalid bit");
		if (bit == 0) {
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		}
		else {
			int value = in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value, 0, null, null);
		}
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits = in.readBits(BITS_PER_INT);
		if (bits!= HUFF_TREE) {
			throw new HuffException("invalid magic number " + bits);
		}
		HuffNode root = readTree(in);
		HuffNode current = root;
		while (true) {
			bits = in.readBits(1); 
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			if (bits == 0) current = current.left; // going left
			else current = current.right; // going right

			if (current.right == null && current.left == null) { // if is a leaf
				if (current.value == PSEUDO_EOF) {
					break; // stop decompressing
				}
				else {
					out.writeBits(BITS_PER_WORD, current.value); 
					current = root;
				}
			}
		}
		out.close();

	}
}