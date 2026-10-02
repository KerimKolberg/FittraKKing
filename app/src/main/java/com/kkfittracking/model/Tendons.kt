package com.kkfittracking.model

/** Where a tendon is, for its drawing. */
enum class TendonArea { KNEE, ANKLE, HIP, SHOULDER, ELBOW, HAND }

/**
 * A tendon (or tendon-like band) that isometric, eccentric and heavy slow exercises load: what it
 * connects, and what it is known for. Not medical advice: a sore tendon is worth a physio's look.
 */
enum class Tendon(val label: String, val area: TendonArea, val connects: String, val about: String) {
    PATELLAR(
        "Patellar tendon", TendonArea.KNEE,
        "the kneecap to the shinbone (tibia)",
        "Passes the pull of the quads on to the lower leg in every squat, jump and landing. Pain just below " +
            "the kneecap is jumper's knee. Heavy holds (Spanish squat, wall sit) often calm it; slow heavy and " +
            "eccentric squats rebuild it.",
    ),
    QUADRICEPS(
        "Quadriceps tendon", TendonArea.KNEE,
        "the four quad muscles to the top of the kneecap",
        "Just above the kneecap. Loaded most in deep knee bends and reverse Nordics.",
    ),
    ACHILLES(
        "Achilles tendon", TendonArea.ANKLE,
        "the calf muscles (gastrocnemius and soleus) to the heel bone",
        "The strongest tendon in the body: it takes several times your bodyweight in running and jumping. " +
            "Heel drops (straight and bent knee) and heavy calf raise holds are the classic ways to strengthen it.",
    ),
    PLANTAR_FASCIA(
        "Plantar fascia", TendonArea.ANKLE,
        "the heel bone to the base of the toes",
        "A tough band under the foot that works as a spring together with the Achilles. Slow calf raises " +
            "(with the toes on a rolled towel) load it.",
    ),
    PERONEAL(
        "Peroneal tendons", TendonArea.ANKLE,
        "the outer lower-leg muscles, around the back of the outer ankle bone, to the foot",
        "They keep the ankle from rolling over when you land or cut sideways: the tendons behind most ankle " +
            "sprains in tennis, volleyball and basketball. Balance holds and banded eversion make them stronger.",
    ),
    POSTERIOR_TIBIAL(
        "Posterior tibial tendon", TendonArea.ANKLE,
        "a deep calf muscle, behind the inner ankle bone, to the underside of the midfoot",
        "Holds up the arch of the foot: pain along the inner ankle and a flattening arch are its warning signs. " +
            "Calf raises squeezing a ball between the heels, banded inversion and short-foot holds load it.",
    ),
    TIBIALIS(
        "Tibialis anterior tendon", TendonArea.ANKLE,
        "the shin muscle to the inner midfoot",
        "Lifts the foot and brakes it when it lands; strong shins help against shin splints.",
    ),
    HAMSTRING(
        "Proximal hamstring tendon", TendonArea.HIP,
        "the hamstrings to the sitting bone (ischial tuberosity)",
        "A deep ache at the sitting bone when sitting or sprinting. Isometric bridges first, then Nordics " +
            "and Romanian deadlifts.",
    ),
    HIP_FLEXOR(
        "Hip flexor tendon (iliopsoas)", TendonArea.HIP,
        "the hip flexors, from the lower spine and inner pelvis, to the inner top of the thigh bone",
        "Pulls the knee up in every sprint stride and kick. Pain at the front of the hip is common in sprinters, " +
            "footballers and kickboxers; slow marches and knee-lift holds load it gently.",
    ),
    ADDUCTOR(
        "Adductor tendons (groin)", TendonArea.HIP,
        "the inner thigh muscles to the pubic bone",
        "Groin pain in football, hockey and skating. Adductor squeezes and Copenhagen planks make them " +
            "stronger and are known to prevent groin injuries.",
    ),
    GLUTEAL(
        "Gluteal tendons", TendonArea.HIP,
        "the side glutes (medius and minimus) to the outer hip bone (greater trochanter)",
        "Pain on the side of the hip, often when lying on it. Side holds and bridges load them gently.",
    ),
    ROTATOR_CUFF(
        "Rotator cuff tendons", TendonArea.SHOULDER,
        "four small muscles from the shoulder blade to the top of the upper arm bone",
        "They hold the ball of the shoulder in its socket; the supraspinatus on top is the one that most " +
            "often hurts. Slow and isometric external rotations keep them strong.",
    ),
    BICEPS(
        "Biceps tendons", TendonArea.SHOULDER,
        "the biceps to the shoulder socket (long head) and to the forearm at the elbow",
        "Pain at the front of the shoulder from pressing and pulling. Slow curls and curl holds load them.",
    ),
    LATERAL_ELBOW(
        "Wrist extensor tendons (tennis elbow)", TendonArea.ELBOW,
        "the forearm muscles that lift the wrist to the outer elbow bone (lateral epicondyle)",
        "Pain on the outside of the elbow when gripping or lifting a cup. The Tyler twist and slow wrist " +
            "extensions are the classic exercises.",
    ),
    MEDIAL_ELBOW(
        "Wrist flexor tendons (golfer's elbow)", TendonArea.ELBOW,
        "the forearm muscles that bend the wrist to the inner elbow bone (medial epicondyle)",
        "Pain on the inside of the elbow from pulling and gripping. Slow wrist curls load them.",
    ),
    TRICEPS(
        "Triceps tendon", TendonArea.ELBOW,
        "the triceps to the point of the elbow (olecranon)",
        "Works hard in dips, skullcrushers and push ups; slow lowering builds it up.",
    ),
    FINGER_FLEXORS(
        "Finger flexor tendons & pulleys", TendonArea.HAND,
        "the forearm flexors to the finger bones, held close to them by ring-like pulleys",
        "The climber's weak link: they adapt more slowly than muscle. Hangs and pinch holds, added to " +
            "little by little, make them stronger.",
    ),
}
