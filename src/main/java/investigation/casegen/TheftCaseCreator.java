package investigation.casegen;

import investigation.llm.LLMClient;
import investigation.model.Case;
import investigation.model.Location;
import investigation.model.Objective;

/** Concrete creator for theft investigations. */
public class TheftCaseCreator extends CaseCreator {
    @Override
    public String getCrimeType() {
        return "theft";
    }

    @Override
    public Case createCase(LLMClient llm, String preferences) throws CaseGenerationException {
        String prompt = "TASK: GENERATE_CASE\nCRIME TYPE: theft\n"
                + "Create an original theft mystery. The subject is the valuable item that was stolen.\n"
                + "Special rule: keyEvidenceId must reveal where the stolen item went or how it was taken, "
                + "and it must point to the culprit. Place it in the restricted location.\n"
                + preferenceLine(preferences) + "\n\n" + SCHEMA;
        Case c = generateValidated(llm, prompt);
        addCommonObjectives(c);
        for (Location l : c.getLocations()) {
            if (l.isRestricted()) {
                c.addObjective(new Objective("Get into the " + l.getName(),
                        Objective.Type.VISIT_LOCATION, l.getId(), 1, "system"));
            }
        }
        if (c.getKeyEvidenceId() != null) {
            c.addObjective(new Objective("Trace the stolen " + c.getSubject(),
                    Objective.Type.FIND_EVIDENCE, c.getKeyEvidenceId(), 1, "system"));
        }
        c.addObjective(new Objective("Identify and accuse the thief",
                Objective.Type.SOLVE_CASE, null, 1, "system"));
        return c;
    }
}
