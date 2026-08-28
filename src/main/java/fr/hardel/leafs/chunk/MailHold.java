package fr.hardel.leafs.chunk;

/** What a piece of mail keeps true until it ran: the chunks around its own, held at a level. A ticking promotion needs its 3x3 at FULL, everything else its chunk present. */
public record MailHold(int radius, Level level) {

    public static final MailHold CHUNK = new MailHold(0, Level.LOADED);
    public static final MailHold FULL_NEIGHBOURHOOD = new MailHold(1, Level.FULL);

    public enum Level {
        LOADED,
        FULL
    }
}
