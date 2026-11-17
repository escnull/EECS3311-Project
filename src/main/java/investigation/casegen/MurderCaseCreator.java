package investigation.casegen;

import investigation.llm.LLMClient;
import investigation.model.Case;
import investigation.model.Objective;

/** Concrete creator for murder investigations. */
public class MurderCaseCreator extends CaseCreator {
    @Override
    public String getCrimeType() {
        return "murder";
    }

    @Override
    public Case createCase(LLMClient llm, String preferences) throws CaseGenerationException {
        String prompt = "TASK: GENERATE_CASE\nCRIME TYPE: murder\n"
                + "Create an original murder mystery. The subject is the victim, who is NOT one of the people.\n"
                + "Special rule: keyEvidenceId must be the murder weapon (or proof of how the victim died), "
                + "and it must point to the culprit.\n"
                + preferenceLine(preferences) + "\n\n" + SCHEMA;
        Case c = generateValidated(llm, prompt);
        addCommonObjectives(c);
        if (c.getKeyEvidenceId() != null) {
            c.addObjective(new Objective("Find the murder weapon or cause of death",
                    Objective.Type.FIND_EVIDENCE, c.getKeyEvidenceId(), 1, "system"));
        }
        c.addObjective(new Objective("Identify and accuse the murderer of " + c.getSubject(),
                Objective.Type.SOLVE_CASE, null, 1, "system"));
        return c;
    }
}
