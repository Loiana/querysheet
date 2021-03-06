package pma;

import querysheet.batch.ResultSetToSpreadsheetBatch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AllocationWeekBatch extends ResultSetToSpreadsheetBatch {

    private static final int MAX_ALLOCATION_WEEKS = 21;

    private static final int ROW_OFFSET = 2;

    private static final int COLUMN_OFFSET_TO_DATES = 4;

    private static final String INTERNAL_CUSTOMER = "Dextra";

    protected static final String COLUMN_NAME_PERCENTAGE = "percentual";
    protected static final String COLUMN_NAME_START = "data_inicio";
    protected static final String COLUMN_NAME_END = "data_termino";
    protected static final String COLUMN_NAME_PERSON = "colaborador_nome";
    protected static final String COLUMN_NAME_CUSTOMER = "cliente_nome";
    protected static final String COLUMN_NAME_PROJECT = "projeto_nome";

    private static final String HEADER_STATUS = "Status";
    private static final String HEADER_PERSON = "Colaborador";
    private static final String HEADER_CUSTOMER = "Cliente";

    private static final int STATUS_COLUMN = 1;
    private static final int PERSON_COLUMN = 2;
    private static final int CUSTOMER_COLUMN = 3;

    private static final int HEADER_ROW = 1;

    private static final int WARNING_WEEKS = 3;

    private static final String ALLOCATION_WARNING = "Atenção";

    private static final String ALLOCATION_FREE = "Livre";

    protected List<AllocationWeek> weeks;

    protected List<String> persons = new ArrayList<>();

    protected Map<String, Map<String, List<AllocationWeek>>> allocation = new HashMap<>();

    protected Map<String, List<String>> personCustomers = new HashMap<>();

    private Date firstStart;
    private Date lastEnd;

    public void load(ResultSet rs) {
        try {
            while (rs.next()) {
                String person = addPerson(rs.getString(COLUMN_NAME_PERSON));

                Date start = adjustStart(rs.getDate(COLUMN_NAME_START));

                if (!validStart(start)) {
                    continue;
                }

                String customer = rs.getString(COLUMN_NAME_CUSTOMER);
                String project = rs.getString(COLUMN_NAME_PROJECT);
                Date end = adjustEnd(rs.getDate(COLUMN_NAME_END));
                Integer percentage = rs.getInt(COLUMN_NAME_PERCENTAGE);

                addAllocation(person, customer, project, start, end, percentage);
            }

            weeks = AllocationWeek.getWeeks(firstStart, lastEnd, 0);
            Collections.sort(persons);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int rows() {
        return persons.size() + 1;
    }

    @Override
    public int cols() {
        return weeks.size() + 1;
    }

    @Override
    public String getValue(int row, int column) {
        if (row == HEADER_ROW) {
            return getHeader(column);
        }

        return getValueInTable(row, column);
    }

    protected String getHeader(int column) {
        if (column == STATUS_COLUMN) {
            return HEADER_STATUS;
        }
        if (column == PERSON_COLUMN) {
            return HEADER_PERSON;
        }
        if (column == CUSTOMER_COLUMN) {
            return HEADER_CUSTOMER;
        }

        return weeks.get(column - COLUMN_OFFSET_TO_DATES).getLabel();
    }

    protected String getValueInTable(int row, int column) {
        String person = persons.get(row - ROW_OFFSET);

        if (column == STATUS_COLUMN) {
            return allocationStatus(person);
        }
        if (column == PERSON_COLUMN) {
            return person;
        }
        if (column == CUSTOMER_COLUMN) {
            return getCustomers(person);
        }

        Integer totalAllocation = getAllocation(person, column - COLUMN_OFFSET_TO_DATES);
        return totalAllocation.toString();
    }

    protected Integer getAllocation(String person, int weekIndex) {
        return getAllocation(person, weeks.get(weekIndex).getKey());
    }

    private String addPerson(String person) {
        if (!persons.contains(person)) {
            persons.add(person);
            allocation.put(person, new HashMap<String, List<AllocationWeek>>());
        }
        return person;
    }

    protected boolean validStart(Date date) {
        if (date == null) {
            return false;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today());
        calendar.add(Calendar.WEEK_OF_YEAR, MAX_ALLOCATION_WEEKS);

        if (date.after(calendar.getTime())) {
            return false;
        }

        return true;
    }

    protected Date adjustEnd(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today());
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
        calendar.add(Calendar.WEEK_OF_YEAR, MAX_ALLOCATION_WEEKS);

        if (date.after(calendar.getTime())) {
            return calendar.getTime();
        }

        return date;
    }

    protected Date adjustStart(Date date) {
        if (date == null) {
            return null;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today());

        if (date.before(calendar.getTime())) {
            return AllocationWeek.adjustToMonday(calendar.getTime());
        }

        return date;
    }

    protected Date today() {
        return new Date();
    }

    private void addAllocation(String person, String customer, String project, Date start, Date end, int percentage) {
        checkAndSetFirstAndLastDates(start, end);
        mergePersonAllocation(person, AllocationWeek.getWeeks(start, end, percentage));
        mergePersonCustomerProject(person, customer, project);
    }

    private void mergePersonCustomerProject(String person, String customer, String project) {
        if (customer == null) {
            return;
        }

        if (!personCustomers.containsKey(person)) {
            personCustomers.put(person, new ArrayList<String>());
        }

        List<String> customers = personCustomers.get(person);

        if (customer.equals(INTERNAL_CUSTOMER)) {
            addIfNotAlreadyAdded(customers, project);
            return;
        }

        addIfNotAlreadyAdded(customers, customer);
    }

    private void addIfNotAlreadyAdded(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private void mergePersonAllocation(String person, List<AllocationWeek> weeks) {
        Map<String, List<AllocationWeek>> personAllocation = allocation.get(person);

        for (AllocationWeek week : weeks) {
            if (!personAllocation.containsKey(week.getKey())) {
                personAllocation.put(week.getKey(), new ArrayList<AllocationWeek>());
            }

            List<AllocationWeek> weekAllocation = personAllocation.get(week.getKey());
            weekAllocation.add(week);
        }
    }

    private void checkAndSetFirstAndLastDates(Date start, Date end) {
        if (firstStart == null || start.before(firstStart)) {
            firstStart = start;
        }

        if (lastEnd == null || end.after(lastEnd)) {
            lastEnd = end;
        }
    }

    private String allocationStatus(String person) {
        Date currentWeek = AllocationWeek.adjustToMonday(today());

        for (int i = 0; i < WARNING_WEEKS; i++) {
            if (getAllocation(person, AllocationWeek.key(currentWeek)) == 0) {
                if (i == 0) {
                    return ALLOCATION_FREE;
                }

                return ALLOCATION_WARNING;
            }
            currentWeek = AllocationWeek.nextWeek(currentWeek);
        }

        return "";
    }

    private String getCustomers(String person) {
        if (!personCustomers.containsKey(person)) {
            return "";
        }

        List<String> customers = personCustomers.get(person);
        Collections.sort(customers);

        StringBuilder builder = new StringBuilder();

        for (String customer : customers) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(customer);
        }

        return builder.toString();
    }

    private Integer getAllocation(String person, String key) {

        Map<String, List<AllocationWeek>> personAllocation = allocation.get(person);

        if (!personAllocation.containsKey(key)) {
            return 0;
        }

        List<AllocationWeek> weeks = personAllocation.get(key);

        int total = 0;

        for (AllocationWeek week : weeks) {
            total += week.getAllocation();
        }

        return total;
    }
}
