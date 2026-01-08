# CurriculumController Endpoints

Base URL: `/api/curriculum`

This document details the endpoints provided by `CurriculumController`. All responses are wrapped in a standard `ResponseDto`.

## Common Response Structure

All successful responses follow this format:

```json
{
  "data": { ... payload ... },
  "error": null
}
```

Error responses follow this format:

```json
{
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": ["Error message detail"],
    "status": "BAD_REQUEST"
  }
}
```

---

## 1. Get Lesson Details

Retrieves detailed information about a specific lesson.

*   **URL**: `/api/curriculum/lesson/{id}`
*   **Method**: `GET`
*   **Path Variables**:
    *   `id` (UUID): The unique identifier of the lesson.
*   **Response**: `ResponseDto<DetailLessonResponse>`

### JSON Response Example

```json
{
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "chapterId": "987fcdeb-51a2-43fe-ba98-765432109876",
    "lessonNumber": 1,
    "name": "Introduction to Physics",
    "description": "Basic concepts of physics",
    "content": "HTML or Text content of the lesson goes here..."
  },
  "error": null
}
```

---

## 2. Get Prompts by Lesson

Retrieves a list of prompts associated with a specific lesson.

*   **URL**: `/api/curriculum/prompt/lesson/{lessonId}`
*   **Method**: `GET`
*   **Path Variables**:
    *   `lessonId` (UUID): The unique identifier of the lesson.
*   **Response**: `ResponseDto<List<PromptLessonResponse>>`

### JSON Response Example

```json
{
  "data": [
    {
      "id": "111e4567-e89b-12d3-a456-426614174000",
      "userId": "222e4567-e89b-12d3-a456-426614174000",
      "collectionId": "333e4567-e89b-12d3-a456-426614174000",
      "title": "Explain Newton's Laws",
      "description": "Prompt to generate explanation for Newton's laws.",
      "instruction": "Explain the three laws of motion...",
      "context": "High school physics context...",
      "inputExample": "What happens if I push a heavy box?",
      "outputFormat": "Structure the answer in bullet points.",
      "constraints": "Keep it under 200 words.",
      "visibility": "PUBLIC",
      "avgRating": 4.5,
      "lessonId": "123e4567-e89b-12d3-a456-426614174000"
    }
  ],
  "error": null
}
```

---

## 3. Get Curriculum Filters

Retrieves the curriculum structure (subjects, grade levels, semesters, chapters, lessons) based on filter criteria. This is likely used to populate dropdowns or curriculum trees.

*   **URL**: `/api/curriculum/filters`
*   **Method**: `GET`
*   **Query Parameters**:
    *   `subjectName` (String, required): Name of the subject (e.g., "Physics").
    *   `gradeLevel` (Integer, required): The grade level (e.g., 10).
    *   `semesterNumber` (Integer, optional): The semester number (e.g., 1).
*   **Response**: `ResponseDto<CurriculumResponse>`

### JSON Response Example

```json
{
  "data": {
    "subjects": [
      {
        "id": "aaaa4567-e89b-12d3-a456-426614174000",
        "name": "Physics",
        "description": "Study of matter and energy"
      }
    ],
    "gradeLevels": [
      {
        "id": "bbbb4567-e89b-12d3-a456-426614174000",
        "level": 10,
        "description": "10th Grade"
      }
    ],
    "semesters": [
      {
        "id": "cccc4567-e89b-12d3-a456-426614174000",
        "semesterNumber": 1,
        "name": "First Semester",
        "listOfChapter": [
          {
            "id": "dddd4567-e89b-12d3-a456-426614174000",
            "semesterId": "cccc4567-e89b-12d3-a456-426614174000",
            "chapterNumber": 1,
            "name": "Kinematics",
            "description": "Motion of objects",
            "listOfLesson": [
              {
                "id": "eeee4567-e89b-12d3-a456-426614174000",
                "chapterId": "dddd4567-e89b-12d3-a456-426614174000",
                "lessonNumber": 1,
                "name": "Velocity and Speed",
                "description": "Defining velocity",
                "content": "HTML or Text content of the lesson goes here..."
              }
            ]
          }
        ]
      }
    ]
  },
  "error": null
}
```
