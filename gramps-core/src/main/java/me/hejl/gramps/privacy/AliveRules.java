package me.hejl.gramps.privacy;

/**
 * Assumptions used to decide whether someone may still be alive. All values are in years.
 *
 * @param maxAge                  oldest age anyone reaches (Gramps: 110)
 * @param maxSiblingAgeDifference largest age gap between siblings (Gramps: 20)
 * @param averageGenerationGap    typical age gap between spouses and between generations (Gramps: 20)
 * @param minGenerationYears      youngest age at which someone has a child (Gramps: 13)
 * @param maxParentAge            oldest age at which someone has a child; not a Gramps setting
 * @param aboutRange              how late an "about", estimated or calculated date may really be
 * @param afterRange              how late an "after" date may really be (Gramps: 50)
 */
public record AliveRules(
        int maxAge,
        int maxSiblingAgeDifference,
        int averageGenerationGap,
        int minGenerationYears,
        int maxParentAge,
        int aboutRange,
        int afterRange) {

    /**
     * Gramps' defaults, plus a maximum parent age. The about range is narrower than Gramps' 50 years,
     * which would keep everyone born "about 1920" hidden as living.
     */
    public static final AliveRules DEFAULTS = new AliveRules(110, 20, 20, 13, 60, 10, 50);

    public AliveRules withMaxAge(int years) {
        return new AliveRules(
                years,
                maxSiblingAgeDifference,
                averageGenerationGap,
                minGenerationYears,
                maxParentAge,
                aboutRange,
                afterRange);
    }
}
