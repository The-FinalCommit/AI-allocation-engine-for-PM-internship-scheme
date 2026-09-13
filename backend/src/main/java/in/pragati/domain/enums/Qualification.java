package in.pragati.domain.enums;
public enum Qualification {
    HIGHER_SECONDARY(1), BACHELORS(2), POST_GRADUATE(3), DOCTORAL(4);
    public final int rank;
    Qualification(int rank) { this.rank = rank; }
}
