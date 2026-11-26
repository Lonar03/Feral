package keystrokesmod.utility;

/** Match scores shared by the block and item search indexes. Inputs are already lowercased. */
final class SearchRanking {
    private SearchRanking() {}

    static int score(String name, String id, String query) {
        if (name.equals(query)) {
            return 1000;
        }
        if (id.equals(query)) {
            return 900;
        }
        if (name.startsWith(query)) {
            return 800;
        }
        if (id.startsWith(query)) {
            return 700;
        }
        for (String token : name.split("\\s+")) {
            if (token.startsWith(query)) {
                return 600;
            }
        }
        if (name.contains(query)) {
            return 500;
        }
        return id.contains(query) ? 400 : 0;
    }
}
