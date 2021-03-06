package querysheet.batch;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TableToSpreadsheetBatch extends ResultSetToSpreadsheetBatch {	

	public void load(ResultSet rs) {
		try {
			loadHeaders(rs);
			loadRows(rs);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private int cols = 0;
	
	List<Object[]> rows = new ArrayList<Object[]>();
	
	private void addRow(Object[] row) {
		if(cols == 0) {
			cols = row.length; 
		}
		rows.add(row);
	}

	@Override
	public int rows() {
		return rows.size();
	}

	@Override
	public int cols() {
		return cols;
	}

	@Override
	public String getValue(int row, int column) {
		Object value = rows.get(row-1)[column-1];
		return value == null ? "null" : formatString(value);
	}

	private void loadHeaders(ResultSet rs) throws SQLException {
		ResultSetMetaData metaData = rs.getMetaData();
		Object[] cols = new Object[metaData.getColumnCount()];
		
		for(int i = 0; i < metaData.getColumnCount(); i++) {
			cols[i] = metaData.getColumnLabel(i+1);
		}
		
		addRow(cols);
	}

	private void loadRows(ResultSet rs) throws SQLException {
		ResultSetMetaData metaData = rs.getMetaData();
		
		while(rs.next()) {
			
			Object[] cols = new Object[metaData.getColumnCount()];
		
			for(int i = 0; i < metaData.getColumnCount(); i++) {								
				cols[i] = rs.getObject(i+1);
			}
			
			addRow(cols);
		}				
	}
}
