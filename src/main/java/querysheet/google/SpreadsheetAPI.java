package querysheet.google;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.List;

import com.google.gdata.client.batch.BatchInterruptedException;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.client.spreadsheet.WorksheetQuery;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.data.spreadsheet.WorksheetFeed;
import com.google.gdata.util.ServiceException;

public class SpreadsheetAPI {

	private SpreadsheetService spreadsheetService;

	private SpreadsheetEntry spreadsheet;

	private WorksheetEntry worksheet;

	public SpreadsheetAPI(SpreadsheetService spreadsheetService) {
		this.spreadsheetService = spreadsheetService;
	}

	public SpreadsheetAPI key(String key) {
		if (spreadsheet != null && spreadsheet.getKey().equals(key)) {
			return this;
		}

		try {
			String spreadsheetURL = "https://spreadsheets.google.com/feeds/spreadsheets/" + key;
			spreadsheet = spreadsheetService.getEntry(new URL(spreadsheetURL), SpreadsheetEntry.class);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	public void setValue(int i, int j, String value) {
		try {
			CellFeed cellFeed = spreadsheetService.getFeed(worksheet.getCellFeedUrl(), CellFeed.class);

			CellEntry cellEntry = new CellEntry(i, j, value);
			cellFeed.insert(cellEntry);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getValue(int i, int j) {
		try {
			CellQuery query = new CellQuery(worksheet.getCellFeedUrl());

			query.setMinimumRow(i);
			query.setMaximumRow(i);
			query.setMinimumCol(j);
			query.setMaximumCol(j);
			query.setMaxResults(1);

			CellFeed cellFeed = spreadsheetService.getFeed(query, CellFeed.class);
			return cellFeed.getEntries().get(0).getCell().getValue();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public SpreadsheetAPI worksheet(String title) {
		if (worksheet != null && worksheet.getTitle().getPlainText().equals(title)) {
			return this;
		}

		try {
			worksheet = getWorksheetByTitle(title);

			if (worksheet == null) {
				worksheet = createWorksheet(title);
			}

			return this;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private WorksheetEntry createWorksheet(String title) throws IOException, ServiceException {
		WorksheetEntry worksheet = new WorksheetEntry();
		worksheet.setTitle(new PlainTextConstruct(title));
		worksheet.setColCount(10);
		worksheet.setRowCount(10);
		return spreadsheetService.insert(spreadsheet.getWorksheetFeedUrl(), worksheet);
	}

	private WorksheetEntry getWorksheetByTitle(String title) throws IOException, ServiceException {
		WorksheetQuery query = new WorksheetQuery(spreadsheet.getWorksheetFeedUrl());

		query.setTitleExact(true);
		query.setTitleQuery(title);

		WorksheetFeed worksheetFeed = spreadsheetService.getFeed(query, WorksheetFeed.class);
		List<WorksheetEntry> worksheets = worksheetFeed.getEntries();

		if (worksheets.size() == 0) {
			return null;
		}

		return worksheets.get(0);
	}

	public void batch(SpreadsheetBatch batch) {
		try {
			CellFeed cellFeed = queryCellFeedForBatch(batch);
			CellFeed batchRequest = createBatchRequest(cellFeed, batch);
			CellFeed batchResponse = executeBatchRequest(cellFeed, batchRequest);

			checkBatchResponse(batchResponse);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private CellFeed executeBatchRequest(CellFeed cellFeed, CellFeed batchRequest) throws IOException,
			ServiceException, BatchInterruptedException, MalformedURLException {
		Link batchLink = cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);

		spreadsheetService.setHeader("If-Match", "*");
		CellFeed batchResponse = spreadsheetService.batch(new URL(batchLink.getHref()), batchRequest);
		spreadsheetService.setHeader("If-Match", null);
		return batchResponse;
	}

	private CellFeed createBatchRequest(CellFeed cellFeed, SpreadsheetBatch batch) {
		List<CellEntry> cellEntries = cellFeed.getEntries();

		CellFeed batchRequest = new CellFeed();

		int count = 0;

		for (int i = 1; i <= batch.rows(); i++) {
			for (int j = 1; j <= batch.cols(); j++) {
				BatchOperationType batchOperationType = BatchOperationType.UPDATE;
				CellEntry sourceEntry = cellEntries.get(count);
				CellEntry batchEntry = new CellEntry(sourceEntry);
				batchEntry.changeInputValueLocal(batch.getValue(i, j));
				BatchUtils.setBatchId(batchEntry, String.valueOf(count));
				BatchUtils.setBatchOperationType(batchEntry, batchOperationType);
				batchRequest.getEntries().add(batchEntry);

				count++;
			}
		}

		return batchRequest;
	}

	private CellFeed queryCellFeedForBatch(SpreadsheetBatch batch) throws IOException, ServiceException {
		adjustWorksheetDimensions(batch);

		CellQuery query = new CellQuery(worksheet.getCellFeedUrl());

		query.setMinimumRow(1);
		query.setMaximumRow(batch.rows());
		query.setMinimumCol(1);
		query.setMaximumCol(batch.cols());

		query.setReturnEmpty(true);
		CellFeed cellFeed = spreadsheetService.getFeed(query, CellFeed.class);
		return cellFeed;
	}

	private void adjustWorksheetDimensions(SpreadsheetBatch updateSet) throws IOException, ServiceException {
		if (worksheet.getColCount() == updateSet.cols() && worksheet.getRowCount() == updateSet.rows()) {
			return;
		}

		worksheet.setColCount(updateSet.cols());
		worksheet.setRowCount(updateSet.rows());

		worksheet.update();
	}

	private void checkBatchResponse(CellFeed batchResponse) {
		for (CellEntry entry : batchResponse.getEntries()) {
			String batchId = BatchUtils.getBatchId(entry);
			if (!BatchUtils.isSuccess(entry)) {
				BatchStatus status = BatchUtils.getBatchStatus(entry);
				throw new RuntimeException(MessageFormat.format("update failed batchId={0}, reason={1}", batchId,
						status.getReason()));
			}
		}
	}
}