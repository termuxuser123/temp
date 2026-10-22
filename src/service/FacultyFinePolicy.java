package service;

//faculty fine policy
public class FacultyFinePolicy implements FinePolicy {
    private static final double RATE = 5.0;
    private static final int GRACE = 3;

    @Override
    public double calculateFine(int days) {
        //3 days grace
        if (days <= GRACE) {
            return 0;
        }

        int d = days - GRACE;
        return d * RATE;
    }
}