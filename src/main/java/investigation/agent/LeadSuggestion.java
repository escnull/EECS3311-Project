package investigation.agent;

import java.util.List;

import investigation.model.Lead;

/** Validated leads proposed by the agent, plus a note for the player. */
public class LeadSuggestion {
    private final List<Lead> leads;
    private final String note;

    public LeadSuggestion(List<Lead> leads, String note) {
        this.leads = leads;
        this.note = note;
    }

    public List<Lead> getLeads() { return leads; }
    public String getNote() { return note; }
}
