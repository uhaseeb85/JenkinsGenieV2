# Deep Dive: The Multi-Dimensional Scoring Mechanism

This document provides an in-depth explanation of the four core algorithms used by the File Selection Service to identify the most relevant source files for fixing a build error. Each scoring mechanism analyzes the project from a different perspective, and their combined, weighted score allows for highly accurate and context-aware file selection.

---

## 1. Semantic Score

**Purpose:** To measure how closely the *meaning* of the code in a file relates to the *meaning* of the error message. This goes far beyond simple keyword matching by understanding the context and relationships between programming concepts.

### How it Works

1.  **Error Message Deconstruction**: The raw error message (e.g., `NullPointerException: Cannot invoke "com.example.User.getName()" because "user" is null`) is broken down into its core semantic components:
    *   **Error Type**: `NullPointerException`
    *   **Key Objects/Classes**: `com.example.User`
    *   **Methods/Functions**: `getName()`
    *   **Variables**: `user`

2.  **Source Code Parsing**: The content of each source file is parsed into an Abstract Syntax Tree (AST). This tree represents the code's structure, not just its text. From the AST, the analyzer extracts key programming elements:
    *   Class, method, and function names.
    *   Variable declarations and types.
    *   Annotations and comments.
    *   Import statements.

3.  **Vectorization**: Both the deconstructed error components and the extracted code elements are converted into high-dimensional numerical vectors using a pre-trained language model (like `Word2Vec`, `GloVe`, or a transformer like `BERT` fine-tuned on code). This model understands the "semantics" of programming.
    *   It knows that `NPE` is a common abbreviation for `NullPointerException`.
    *   It understands that a variable named `user` is likely an instance of a `User` class.
    *   It can infer that a file containing `class User { ... }` is highly relevant to an error mentioning `com.example.User`.

4.  **Similarity Calculation**: The system calculates the **cosine similarity** between the vector representing the error message and the vector representing the source file.
    *   A score close to **1.0** means the file is highly relevant.
    *   A score close to **0.0** means the file is likely irrelevant.

### Pseudocode

```java
class SemanticAnalyzer {
    // Pre-trained model that understands code semantics
    private CodeLanguageModel codeModel;

    /**
     * Calculates the semantic similarity between a file's content and an error message.
     */
    public double calculateSimilarity(SourceFile file, BuildError error, ProjectContext context) {
        // 1. Deconstruct the error message into its core parts
        List<String> errorTokens = extractTokensFromError(error.getMessage());

        // 2. Parse the source file to extract its key elements
        List<String> fileTokens = extractTokensFromFile(file.getContent(), context.getLanguage());

        // 3. Convert both sets of tokens into numerical vectors
        Vector errorVector = codeModel.createVector(errorTokens);
        Vector fileVector = codeModel.createVector(fileTokens);

        // 4. Calculate the cosine similarity between the two vectors
        double similarity = cosineSimilarity(errorVector, fileVector);

        return normalize(similarity); // Normalize to a 0.0 - 1.0 scale
    }

    private List<String> extractTokensFromError(String errorMessage) {
        // ... logic to parse error, find class names, method names, etc.
        return parsedTokens;
    }

    private List<String> extractTokensFromFile(String fileContent, Language language) {
        // ... logic to parse file using an AST, find declarations, comments, etc.
        return parsedTokens;
    }
}
```

---

## 2. Dependency Score

**Purpose:** To evaluate a file's relevance based on its relationships with other components mentioned in the error's stack trace. A file is important if it uses a problematic component, or if a problematic component uses it.

### How it Works

1.  **Build a Project-Wide Dependency Graph**: Before scoring, the system analyzes the entire project to map out all relationships. It knows which files `import` or `require` which other files, creating a complete graph of the codebase.

2.  **Identify Error Components**: The service extracts the full path of classes/modules from the error stack trace (e.g., `com.example.service.OrderService`, `com.example.model.User`).

3.  **Calculate Relevance Score based on Graph Distance**:
    *   **Direct Dependency (High Score)**: If `FileA` directly imports `OrderService` (which appeared in the stack trace), it gets a high score.
    *   **Transitive Dependency (Medium Score)**: If `FileA` imports `PaymentService`, and `PaymentService` in turn imports `OrderService`, `FileA` is still relevant, but less so. The score decreases as the distance in the dependency graph increases.
    *   **Reverse Dependency (High Score)**: If `FileA` defines the `User` class, and the problematic `OrderService` imports `User`, then `FileA` is critically important. This "depends on me" relationship is a strong indicator of relevance.

### Pseudocode

```java
class DependencyService {
    // A pre-computed graph of all dependencies in the project
    private DependencyGraph projectGraph;

    /**
     * Calculates relevance based on the file's position in the dependency graph
     * relative to components in the error's stack trace.
     */
    public double calculateDependencyRelevance(SourceFile file, BuildError error, ProjectContext context) {
        // 1. Get the set of components directly mentioned in the stack trace
        Set<Component> errorComponents = error.getRelatedComponents();
        
        // 2. Get the component defined by the current file
        Component currentFileComponent = file.getDefinedComponent();

        double maxScore = 0.0;

        for (Component errorComponent : errorComponents) {
            // 3. Check for both forward and reverse dependencies
            double forwardDistance = projectGraph.getDistance(currentFileComponent, errorComponent);
            double reverseDistance = projectGraph.getDistance(errorComponent, currentFileComponent);

            // 4. Calculate score, giving higher value to closer dependencies
            // (Inverse relationship: distance 1 is better than distance 3)
            double score = 0.0;
            if (forwardDistance > 0) {
                score = Math.max(score, 1.0 / forwardDistance);
            }
            if (reverseDistance > 0) {
                score = Math.max(score, 1.0 / reverseDistance);
            }
            
            maxScore = Math.max(maxScore, score);
        }

        return normalize(maxScore);
    }
}
```

---

## 3. Architectural Score

**Purpose:** To assess a file's importance based on its role within the application's architecture (e.g., MVC, Microservices). Some file types are inherently more likely to contain business logic and cause errors.

### How it Works

1.  **Pattern and Annotation Analysis**: The system scans the file for clues about its architectural role. This is highly language- and framework-specific:
    *   **Java (Spring)**: It looks for annotations like `@RestController`, `@Service`, `@Repository`, `@Configuration`.
    *   **JavaScript (React)**: It identifies files as components, hooks, or utility functions based on naming conventions and syntax.
    *   **Python (Django)**: It identifies `views.py`, `models.py`, and `serializers.py` by their conventional names and locations.

2.  **Assign Role-Based Weights**: Each architectural role is assigned a weight based on its likelihood of containing complex logic.
    *   **High Weight**: Controllers, Services, Models.
    *   **Medium Weight**: Repositories, Utility classes, Configuration files that define beans/services.
    *   **Low Weight**: Simple DTOs (Data Transfer Objects), constants, or basic configuration files.

### Pseudocode

```java
class ArchitecturalAnalyzer {
    // A map of framework-specific patterns to their importance scores
    private Map<Pattern, Double> architecturalWeights;

    /**
     * Assesses a file's architectural role and assigns a score.
     */
    public double assessArchitecturalPatterns(SourceFile file, ProjectContext context) {
        String fileContent = file.getContent();
        String filePath = file.getPath();
        double score = 0.0;

        // 1. Load the appropriate weightings for the project's language and framework
        Map<Pattern, Double> weights = getWeightsForFramework(context.getFramework());

        // 2. Check for file path conventions (e.g., 'controller' in path)
        for (Pattern pathPattern : weights.keySet()) {
            if (pathPattern.matcher(filePath).find()) {
                score = Math.max(score, weights.get(pathPattern));
            }
        }

        // 3. Check file content for annotations or keywords (e.g., "@Service")
        for (Pattern contentPattern : weights.keySet()) {
            if (contentPattern.matcher(fileContent).find()) {
                score = Math.max(score, weights.get(contentPattern));
            }
        }
        
        // Special case for build system files if it's a compilation error
        if (context.getError().getType() == ErrorType.COMPILATION) {
            if (filePath.endsWith("pom.xml") || filePath.endsWith("build.gradle")) {
                score = 1.0; // Highest possible score
            }
        }

        return normalize(score);
    }
}
```

---

## 4. Historical Score

**Purpose:** To leverage past data to predict which files are likely to be involved in a fix for a given type of error. It operates on the principle that history often repeats itself, quickly identifying recurring "problem spots" in the codebase.

### How it Works

1.  **Maintain a Fix History Database**: The system logs every successful fix, storing a record that links:
    *   The **error type** (e.g., `NullPointerException`, `SQLException`).
    *   The **files that were modified** to fix the error.
    *   The **commit hash** and timestamp of the fix.

2.  **Calculate a Predictive Score**: When a new error occurs, the system queries this database.
    *   It looks for the current error type (`error.getType()`).
    *   It checks how many times the file being scored (`file.getPath()`) has been modified in the past to fix that same type of error.
    *   The score is a function of this frequency.

3.  **Decay Factor (Optional)**: The score can be weighted by time. More recent fixes can be considered more relevant than older ones, preventing outdated patterns from skewing the results.

### Pseudocode

```java
class HistoricalAnalyzer {
    // Database connection to query past fix data
    private FixHistoryDatabase db;

    /**
     * Calculates a score based on how often this file has been involved
     * in fixing similar errors in the past.
     */
    public double getHistoricalFixScore(SourceFile file, BuildError error, ProjectContext context) {
        
        // 1. Query the database for past fixes
        List<FixRecord> records = db.query(
            "SELECT * FROM fixes WHERE error_type = ? AND file_path = ?",
            error.getType().toString(),
            file.getPath()
        );

        if (records.isEmpty()) {
            return 0.0;
        }

        // 2. Calculate a score based on frequency and recency
        double totalScore = 0.0;
        for (FixRecord record : records) {
            // Apply a time decay factor: more recent fixes are more important
            long daysSinceFix = ChronoUnit.DAYS.between(record.getTimestamp(), Instant.now());
            double decay = Math.exp(-0.01 * daysSinceFix); // Exponential decay
            totalScore += decay;
        }

        // 3. Normalize the score based on the max possible score or total number of fixes
        return normalize(totalScore, db.getTotalFixCountForError(error.getType()));
    }
}
```
