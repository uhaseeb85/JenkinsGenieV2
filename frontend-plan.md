# CI Fixer Frontend Development Plan

## Project Overview
Development of a professional frontend interface for the CI Fixer application to monitor Jenkins build processes and view results with PR links.

**Backend Port**: 8081 (corrected)
**Frontend Integration**: Bundled with existing Spring Boot application
**Status**: Document created - awaiting approval to begin Phase 1

---

## Technical Architecture

### Technology Stack
- **Frontend**: React 18 + TypeScript
- **Styling**: Tailwind CSS + shadcn/ui
- **Build Tool**: Vite
- **State Management**: React Query (TanStack Query)
- **HTTP Client**: Axios
- **Icons**: Lucide React
- **Deployment**: Static files served by Spring Boot (Port 8081)

### Integration Strategy
```
┌─────────────────────────────────────┐
│           Docker Container          │
├─────────────────────────────────────┤
│  Spring Boot App (Port 8081)       │  ← CORRECTED PORT
│  ├── REST APIs (/api/*)            │
│  ├── Static Resources (/*)         │
│  └── React App (built files)       │
├─────────────────────────────────────┤
│  PostgreSQL Database                │
└─────────────────────────────────────┘
```

---

## API Design

### Required New Endpoints
```typescript
GET /api/builds                    // List all builds with pagination
GET /api/builds/{id}              // Get specific build details
GET /api/builds/{id}/tasks        // Get all tasks for a build
GET /api/builds/{id}/status       // Get current processing status
```

### Response Models
```typescript
interface Build {
  id: number;
  job: string;
  buildNumber: number;
  status: 'PROCESSING' | 'SUCCESS' | 'FAILED';
  createdAt: string;
  completedAt?: string;
  repoUrl: string;
  branch: string;
  commitSha: string;
  errorCount: number;
  prUrl?: string;
  prNumber?: number;
}

interface BuildDetail extends Build {
  tasks: Task[];
  errors: ErrorInfo[];
  patchedFiles: string[];
}

interface Task {
  id: number;
  type: 'PLAN' | 'REPO' | 'RETRIEVE' | 'PATCH' | 'VALIDATE' | 'CREATE_PR' | 'NOTIFY';
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  startedAt?: string;
  completedAt?: string;
  metadata: Record<string, any>;
}
```

---

## Development Phases

### Phase 1: Foundation ⏳ PENDING APPROVAL
**Goal**: Basic infrastructure and builds table

#### Backend Tasks
- [ ] Create BuildApiController with /api/builds endpoint
- [ ] Create BuildService for business logic
- [ ] Create DTOs (BuildSummary, BuildDetail, PagedResponse)
- [ ] Configure CORS for port 8081
- [ ] Configure static resource serving

#### Frontend Tasks
- [ ] Initialize React + TypeScript + Vite project
- [ ] Set up Tailwind CSS and shadcn/ui
- [ ] Create basic App shell and routing
- [ ] Implement BuildsTable component
- [ ] Create StatusBadge component
- [ ] Set up API service layer (pointing to port 8081)

#### Docker Integration
- [ ] Update Dockerfile with multi-stage build
- [ ] Frontend build stage (Node.js)
- [ ] Copy built files to Spring Boot static resources
- [ ] Test full container build and deployment

#### Success Criteria
- [ ] Frontend loads at http://localhost:8081/
- [ ] Builds table displays data from API
- [ ] Status badges show correct colors
- [ ] Docker container serves both frontend and backend
- [ ] No console errors

### Phase 2: Core Functionality ⏳ NOT STARTED
**Goal**: Build details, task pipeline, and PR integration

#### Backend Tasks
- [ ] Implement /api/builds/{id} endpoint
- [ ] Implement /api/builds/{id}/tasks endpoint
- [ ] Add PR URL fields to Build model
- [ ] Update BuildService with detail methods

#### Frontend Tasks
- [ ] Create BuildDetailModal component
- [ ] Implement TaskPipeline visualization
- [ ] Add ErrorsList component
- [ ] Implement status polling mechanism
- [ ] Add PR link integration
- [ ] Replace mock data with real API calls

#### Success Criteria
- [ ] Build detail modal opens with complete information
- [ ] Task pipeline shows visual progress
- [ ] PR links are functional
- [ ] Real-time status updates work

### Phase 3: Enhancement ⏳ NOT STARTED
**Goal**: Polish, performance, and responsive design

#### Tasks
- [ ] Responsive design improvements
- [ ] Loading states and error handling
- [ ] Performance optimization
- [ ] Accessibility improvements
- [ ] Final testing and bug fixes

#### Success Criteria
- [ ] Professional, polished appearance
- [ ] Fast loading times (<2 seconds)
- [ ] Works on tablets and desktops
- [ ] Proper error handling

---

## File Structure Plan

### Backend Changes (to be added)
```
src/main/java/com/example/cifixer/
├── web/
│   ├── BuildApiController.java     (NEW)
│   ├── dto/
│   │   ├── BuildSummary.java       (NEW)
│   │   ├── BuildDetail.java        (NEW)
│   │   └── PagedResponse.java      (NEW)
├── service/
│   └── BuildService.java           (NEW)
```

### Frontend Structure (to be created)
```
frontend/                            (Development)
├── public/
├── src/
│   ├── components/
│   │   ├── ui/                     (shadcn components)
│   │   ├── BuildsTable.tsx
│   │   ├── BuildDetailModal.tsx
│   │   ├── StatusBadge.tsx
│   │   └── TaskPipeline.tsx
│   ├── services/
│   │   └── api.ts                  (port 8081 config)
│   ├── types/
│   │   └── index.ts
│   ├── App.tsx
│   └── main.tsx
├── package.json
├── vite.config.ts
├── tailwind.config.js
└── tsconfig.json
```

### Production Structure (after build)
```
src/main/resources/static/           (NEW - React build output)
├── index.html
├── assets/
│   ├── css/
│   └── js/
```

---

## Docker Integration Plan

### Multi-stage Dockerfile Updates
```dockerfile
# Frontend build stage (NEW)
FROM node:18-alpine as frontend-builder
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Backend build stage (EXISTING - with frontend integration)
FROM maven:3.8.4-openjdk-11 as backend-builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Copy built frontend files (NEW)
COPY --from=frontend-builder /frontend/dist ./src/main/resources/static/
RUN mvn clean package -DskipTests

# Runtime stage (EXISTING)
FROM openjdk:11-jre-slim
# ... existing runtime setup ...
COPY --from=backend-builder /app/target/*.jar app.jar
# Frontend files are now bundled in the jar
EXPOSE 8081
```

---

## UI Design Specifications

### Layout Structure
```
┌─────────────────────────────────────┐
│              Header                 │
│     CI Fixer Dashboard              │
├─────────────────────────────────────┤
│                                     │
│           Builds Table              │
│  ┌─────┬────────┬────────┬────────┐ │
│  │ Job │ Build  │ Status │ PR     │ │
│  ├─────┼────────┼────────┼────────┤ │
│  │ ... │   ...  │   ...  │  ...   │ │
│  └─────┴────────┴────────┴────────┘ │
│                                     │
│         [Load More Button]          │
├─────────────────────────────────────┤
│              Footer                 │
└─────────────────────────────────────┘
```

### Component Hierarchy
```
App
├── Header
├── BuildsPage
│   ├── BuildsTable
│   │   ├── BuildRow
│   │   └── StatusBadge
│   └── LoadMoreButton
├── BuildDetailModal
│   ├── BuildInfo
│   ├── TaskPipeline
│   │   └── TaskStep
│   └── ErrorsList
└── Footer
```

### Status Colors
- **Processing**: `bg-amber-100 text-amber-800`
- **Success**: `bg-green-100 text-green-800`
- **Failed**: `bg-red-100 text-red-800`
- **Pending**: `bg-gray-100 text-gray-800`

---

## Configuration Details

### API Base URL
```typescript
// services/api.ts
const API_BASE_URL = 'http://localhost:8081/api';  // Updated port
```

### CORS Configuration (Backend)
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowCredentials(false);
    }
}
```

### Static Resource Configuration (Backend)
```java
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .setCachePeriod(86400);
    }
}
```

---

## Development Notes

### Session Continuity Information
- **Current Status**: Document created - awaiting approval to begin Phase 1
- **Backend Port**: 8081 (confirmed)
- **No Authentication Required**: Public access interface
- **Minimal Features**: Focus on build monitoring and PR links only
- **Integration Method**: Bundled in same Docker container

### Key Decisions Made
1. React + TypeScript for type safety
2. Tailwind + shadcn/ui for professional appearance
3. Vite for fast development and optimized builds
4. React Query for server state management
5. Multi-stage Docker build for efficiency
6. Static file serving from Spring Boot

### Future Session Pickup Points
- If resuming during Phase 1: Check Phase 1 task completion status
- If resuming during Phase 2: Verify Phase 1 completion before proceeding
- Always check Docker container build status first
- Verify backend API endpoints are working before frontend integration

### Existing Database Schema Knowledge
Based on current system, the following tables exist:
- `builds` table with fields: id, job, build_number, repo_url, branch, commit_sha, status, created_at, completed_at
- `tasks` table with fields: id, build_id, type, status, attempt, created_at, completed_at, metadata
- `pull_requests` table with PR information linked to builds

---

## Status: ⏳ DOCUMENT CREATED - AWAITING APPROVAL TO BEGIN PHASE 1

**Next Action**: Wait for go-ahead to start Phase 1 implementation

---

*Last Updated: August 21, 2025*
*Session: Initial Planning - Document Creation*
