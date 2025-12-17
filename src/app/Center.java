package app;

public class Center {
    String id;
    double lat, lng;
    String status; // e.g., "Open", "Closed", "Busy"
    String guidelines; // brief text on accepted materials / times
    String barangayName; // barangay name for display

    public Center(String id, double lat, double lng) {
        this(id, lat, lng, "Open", "General recycling accepted", "Unknown");
    }

    public Center(String id, double lat, double lng, String status, String guidelines) {
        this(id, lat, lng, status, guidelines, "Unknown");
    }

    public Center(String id, double lat, double lng, String status, String guidelines, String barangayName) {
        this.id = id;
        this.lat = lat;
        this.lng = lng;
        this.status = status;
        this.guidelines = guidelines;
        this.barangayName = barangayName;
    }

    public String getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public String getStatus() {
        return status;
    }

    public String getGuidelines() {
        return guidelines;
    }

    public String getBarangayName() {
        return barangayName;
    }

    // Setter methods for admin editing
    public void setStatus(String newStatus) {
        this.status = newStatus;
    }

    public void setGuidelines(String newGuidelines) {
        this.guidelines = newGuidelines;
    }
}
