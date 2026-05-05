# KStream App Specification

This document serves as the primary context for building the KStream OTT application (Mobile and TV). It defines the database schema, data relationships, and the logic required to display and play content.

---

## 1. Database Architecture (Supabase)

### **Table: `movies` (The Core Catalog)**
This table stores the high-level information for every movie.
*   **`id`** (UUID): Primary Key.
*   **`movie_name`** (Text): The display title of the movie.
*   **`year`** (Integer): Release year (useful for filtering).
*   **`poster_url`** (Text): Full URL to the movie poster image.
*   **`duration`** (Text): Total runtime (e.g., "02:35:07 min").
*   **`synopsis`** (Text): Plot summary.
*   **`director`** (Text[]): Array of director names.
*   **`cast_members`** (Text[]): Array of starring actors.
*   **`genres`** (Text[]): Array of categories (e.g., ["Action", "Drama"]).
*   **`rating`** (Text): Movie rating (e.g., "6.9").
*   **`language`** (Text): Primary language (e.g., "Tamil").
*   **`type`** (Text): General quality type (e.g., "Original HD").
*   **`slug`** (Text): Unique URL path (used for matching and deep-linking).

### **Table: `media` (The Playable Links)**
This table stores the specific video links for different qualities.
*   **`movie_id`** (UUID): Foreign Key linking back to `movies.id`.
*   **`quality`** (Text): The label (e.g., "1080p HD", "720p HD", "360p HD").
*   **`file_size`** (Text): The size of the file (e.g., "1.4 GB").
*   **`download_url_1`** / **`download_url_2`** (Text): Direct `.mp4` URLs.
*   **`watch_url_1`** / **`watch_url_2`** (Text): Streaming-optimized URLs (usually ends in `.mp4?stream=1`).

---

## 2. Core App Logic & Workflows

### **A. Home Screen (Discovery)**
*   **Query**: Fetch movies ordered by `updated_at` or `year`.
*   **Usage**: Display the `poster_url` and `movie_name`.
*   **Optimization**: Only fetch `id`, `movie_name`, `poster_url`, and `year` for the list view to keep the initial load fast.

### **B. Movie Details Screen**
*   **Query**: Fetch the full movie row and **all associated media rows**.
*   **Supabase Join**: 
    ```javascript
    const { data } = await supabase
      .from('movies')
      .select('*, media(*)')
      .eq('id', MOVIE_ID)
      .single();
    ```
*   **Display**:
    *   Show metadata (Cast, Director, Synopsis).
    *   List the available qualities found in the `media` array as "Select Quality" buttons.

### **C. Video Playback**
*   **Logic**: 
    1. User selects a quality (e.g., "720p HD").
    2. App prioritizes `watch_url_1`. If it fails or is null, fallback to `watch_url_2`.
    3. If both watch URLs are null, the app can attempt to play the `download_url_1` as a stream.
*   **Player Requirement**: The player must handle standard `.mp4` and progressive stream URLs.

---

## 3. Implementation Guidelines

### **Data Refreshing**
The app is "Read-Only." All data is managed by the Scrapper. The app should always fetch live data from Supabase to ensure links (which can expire or change domains) are always up to date.

### **Search and Filtering**
*   **Search**: Implement a text search on the `movie_name` column.
*   **Filtering**: Allow users to filter by `year` or `genres`. (Postgres handles array-contains queries for genres).

### **Handling "Slug" for Deep-Linking**
The `slug` column (e.g., `/happy-raj-2026-tamil-movie/`) should be used if you want to share links to specific movies or implement a web-version of the app later.

---
**Context Note**: This document is the source of truth for the frontend agent. Use the table fields exactly as defined here to avoid database errors.
