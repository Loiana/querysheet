package querysheet.google;

public interface SpreadsheetBatch {

	public int rows();

	public int cols();

	// 1-based indexes
	public String getValue(int row, int column);

}
