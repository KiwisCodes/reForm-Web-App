Related Links

[Stitch - Projects](https://stitch.withgoogle.com/projects/17744872572285525493)

Javascript

# **The Complete JavaScript ****&**** React Transition Guide (Java Developer Edition)**

## **PART 1: The Foundations (Transcript Code Breakdown)**

*This section covers the baseline concepts introduced in the slot machine transcript.*

### **1. Environment ****&**** Setup Details**

In Java, you compile .java files into .class files and run them on the JVM. In JavaScript (outside the browser), you use **Node.js** to execute code directly in the terminal.

- **npm init**: Creates a package.json file. This is roughly equivalent to a pom.xml in Maven or build.gradle in Gradle. It tracks your project's dependencies.

- **require()**: Before modern ES Modules (import), Node.js used CommonJS syntax require() to pull in external libraries.

JavaScript

// Imports the prompt-sync library and immediately invokes it with ()

const prompt = require("prompt-sync")(); 

### **2. Variables ****&**** Mutability**

Java requires strict type declarations (int, String, boolean). JavaScript is dynamically typed. You declare variables based on whether their memory reference can be reassigned.

- **const**: Cannot be reassigned. Use this by default for arrays, objects, and static values.

- **let**: Can be reassigned. Use this for counters or values that update.

JavaScript

// The slot machine grid won't change size, so we use const

const ROWS = 3;

const COLS = 3;

// The user's balance changes as they win/lose, so we use let

let balance = 100;

balance = balance - 10; // Valid

// ROWS = 4; // ERROR: Assignment to constant variable.

### **3. ES6 Arrow Functions vs Standard Functions**

Java binds methods to classes. JavaScript functions can live entirely on their own. Modern JavaScript heavily relies on Arrow Functions.

JavaScript

// Standard approach (older, similar to Java methods)

function getDeposit() {

    // code

}

// Arrow function approach (modern, used in the video and heavily in React)

const getDeposit = () => {

    // code

};

### **4. Input Validation ****&**** Control Flow**

Node.js requires external packages like prompt-sync for synchronous terminal input. The transcript uses an infinite while(true) loop to trap the user until they enter valid data.

- **parseFloat()**: Converts a string input ("50.5") into a floating-point number.

- **isNaN()**: "Is Not a Number". If you try to parse text ("Hello") as a number, JS doesn't throw a NumberFormatException like Java; it returns the NaN value.

JavaScript

const deposit = () => {

  while (true) {

    const depositAmount = prompt("Enter a deposit amount: ");

    const numberDepositAmount = parseFloat(depositAmount); // Convert String to Number

    // If it's text (NaN) OR a negative number, yell at the user

    if (isNaN(numberDepositAmount) || numberDepositAmount <= 0) {

      console.log("Invalid deposit amount, try again.");

    } else {

      return numberDepositAmount; // Breaks the infinite loop

    }

  }

};

### **5. Objects as Maps**

In Java, you need a HashMap<String, Integer>. In JavaScript, a plain Object literal {} functions as a key-value dictionary out of the box.

JavaScript

const symbolsCount = {

  A: 2, // Key 'A' mapped to Value 2

  B: 4,

  C: 6,

  D: 8

};

console.log(symbolsCount["A"]); // Returns 2

## **PART 2: JavaScript vs. Java (The Core Paradigm Shifts)**

*This section covers the advanced JavaScript syntaxes that handle programming entirely differently than Java.*

### **6. Equality: ****==**** vs ****===**** (The ****"****Weird****"**** Checks)**

Java uses == for primitive comparison and .equals() for object comparison. JavaScript has two equality operators.

- **Loose Equality (****==****)**: JavaScript will try to guess and convert the types before comparing them. **Never use this.**

- **Strict Equality (****===****)**: Checks both the value AND the data type. **Always use this.**

JavaScript

console.log(5 == "5");   // true (JS converted the string to a number under the hood)

console.log(5 === "5");  // false (Number vs String)

console.log(0 === false);// false

### **7. Object ****&**** Array Destructuring**

Instead of calling getter methods (user.getName()), JavaScript allows you to unpack properties directly into variables.

JavaScript

// Object Destructuring

const player = { username: "Cloud", level: 99, hp: 9999 };

const { username, hp } = player;

console.log(username); // "Cloud"

// Array Destructuring

const coordinates = [150, 400];

const [x, y] = coordinates; // x becomes 150, y becomes 400

### **8. The Spread and Rest Pattern (****...****)**

Looks like Java's varargs (...args), but infinitely more versatile.

- **Spread (Unpacking)**: Used to easily copy or merge arrays/objects.

JavaScript

const defaultStats = { str: 10, dex: 10 };

const warriorStats = { ...defaultStats, str: 20, hp: 100 }; 

// Result: { str: 20, dex: 10, hp: 100 }

const list1 = [1, 2, 3];

const list2 = [...list1, 4, 5]; // Result: [1, 2, 3, 4, 5]

- **Rest (Packing)**: Gathers an unknown number of function arguments into an array.

JavaScript

const sumAll = (...numbers) => {

    return numbers.reduce((total, num) => total + num, 0);

};

console.log(sumAll(1, 5, 10)); // 16

### **9. Higher-Order Functions ****&**** Closures**

Functions are variables. Therefore, a function can return another function. This creates a "Closure" (a function that remembers the variables from where it was created).

JavaScript

const createMultiplier = (multiplier) => {

    return (number) => {

        return number * multiplier; // Remembers 'multiplier'

    };

};

const doubleNumber = createMultiplier(2);

console.log(doubleNumber(10)); // 20

### **10. Asynchronous Logic: ****fetch**** and ****await**

Java uses Threads or CompletableFuture. JavaScript is strictly single-threaded. It uses Promises to prevent the app from freezing. async/await forces JS to wait for a Promise to resolve before moving to the next line.

JavaScript

const getUserData = async () => {

    try {

        // Pauses execution of THIS function until API responds

        const response = await fetch("https://api.github.com/users/octocat");

        const data = await response.json();

        console.log(data.name);

    } catch (error) {

        console.log("Failed to fetch data", error);

    }

};

## **PART 3: The 24 Essential Concepts to Transition to React**

*React is just modern JavaScript. To master React, you must master these ES6+ concepts.*

### **Category A: Truth, Logic, and Conditional Rendering**

*React uses these inside JSX to show/hide UI components.*

**11. Truthy and Falsy Values**

Java requires strict boolean types. JS evaluates *any* value as truthy or falsy. Falsy values are: false, 0, "", null, undefined, and NaN. Everything else is truthy.

JavaScript

const user = ""; 

if (!user) console.log("Falsy! User is missing.");

**12. Short-Circuit Evaluation (****&****&**** and ****||****)**

React uses && to render a component *only if* a condition is true. It returns the actual value, not just a boolean.

JavaScript

const isLoggedIn = true;

// In React JSX: { isLoggedIn && <Dashboard /> }

const username = null || "Guest"; // Evaluates to "Guest"

**13. The Ternary Operator (****? :****)**

Standard if/else blocks are illegal inside React JSX. You must use ternaries.

JavaScript

const status = isOnline ? "Green Dot" : "Red Dot";

**14. Nullish Coalescing (****??****)**

Similar to ||, but strictly checks for null or undefined. Prevents bugs where 0 or "" accidentally trigger a fallback.

JavaScript

const score = 0;

const displayScore = score ?? "N/A"; // Returns 0. (|| would return "N/A")

**15. Optional Chaining (****?.****)**

Prevents NullPointerExceptions when accessing deeply nested properties that might be missing.

JavaScript

const user = { name: "John" };

console.log(user.address?.zipCode); // Returns 'undefined' instead of crashing

### **Category B: Functional Array Manipulation**

*React expects data to be immutable. You will never use a traditional **for** loop. You will use these functional methods to create new arrays.*

**16. ****.map()**

Transforms every item in an array and returns a completely new array. (Equivalent to Java Stream.map()).

JavaScript

const prices = [10, 20, 30];

const discountedPrices = prices.map(price => price * 0.8); // [8, 16, 24]

**17. ****.filter()**

Creates a new array containing only elements that pass a test. Vital for deleting items from a React list.

JavaScript

const numbers = [1, 2, 3, 4];

const evens = numbers.filter(n => n % 2 === 0); // [2, 4]

**18. ****.reduce()**

Reduces an array to a single value.

JavaScript

const cart = [{price: 10}, {price: 20}];

const total = cart.reduce((sum, item) => sum + item.price, 0); // 30

**19. ****.find()**** and ****.findIndex()**

.find() returns the first item that matches a condition. .findIndex() returns its position.

JavaScript

const users = [{id: 1, name: "Bob"}, {id: 2, name: "Alice"}];

const alice = users.find(u => u.name === "Alice"); // {id: 2, name: "Alice"}

**20. ****.some()**** and ****.every()**

.some() checks if *at least one* item meets a condition. .every() checks if *all* items do.

JavaScript

const hasAdmin = users.some(u => u.role === 'admin'); // returns boolean

**21. ****.includes()**

Checks if an array or string contains a specific value (Java equivalent: list.contains()).

JavaScript

const roles = ["user", "editor"];

console.log(roles.includes("admin")); // false

### **Category C: Advanced Objects ****&**** Data Structures**

*React state is almost always an object.*

**22. Object Property Shorthand**

If your variable name matches the object key, don't type it twice.

JavaScript

const name = "Jane";

const age = 25;

const user = { name, age }; // Instead of { name: name, age: age }

**23. Computed Property Names**

Use variables to dynamically set an object's key using brackets [].

JavaScript

const keyName = "status";

const task = { id: 1, [keyName]: "Done" }; // { id: 1, status: "Done" }

**24. Object Iteration (****keys****, ****values****, ****entries****)**

Objects aren't arrays, so you can't .map() them. Convert them first.

JavaScript

const scores = { math: 90, english: 85 };

console.log(Object.keys(scores)); // ["math", "english"]

console.log(Object.entries(scores)); // [["math", 90], ["english", 85]]

### **Category D: Architecture, Modules ****&**** Scope**

*React applications are built using hundreds of isolated files. You must know how to share code between them.*

**25. Named vs. Default Exports**

JavaScript

// Default Export (One per file, you can import it and name it whatever you want)

export default function Button() {} 

// Named Export (Multiple per file, MUST be imported with exact name)

export const API_KEY = "123";

export function helper() {}

**26. Named vs. Default Imports**

JavaScript

import Button from './Button'; // Default

import { API_KEY, helper } from './utils'; // Named (needs brackets)

**27. Default Parameters**

Assign fallback values to function arguments.

JavaScript

const greet = (name = "Guest") => console.log(`Hello ${name}`);

**28. Lexical ****this**

In Java, this is the class instance. In older JS, this is the object that called the function. Arrow functions change this: they inherit this from the surrounding scope. (React Functional Components rarely use this, but it appears in class-based React).

**29. Template Literals (String Interpolation)**

Use backticks (`) to inject variables directly.

JavaScript

const item = "Coffee";

const message = `I would like a ${item}.`;

### **Category E: Memory ****&**** Side Effects**

*React relies heavily on memory references to know when to update the screen.*

**30. Value vs. Reference Types**

Primitives (strings/numbers) are passed by value. Objects/Arrays are passed by memory reference.

JavaScript

const arr1 = [1];

const arr2 = arr1; // arr2 points to the exact same memory block as arr1

arr2.push(2);

console.log(arr1); // [1, 2] - Modifying arr2 modified arr1!

**31. Immutability**

Because of references, React requires strict immutability. You never change an array/object directly (e.g., .push()). You make a spread copy, modify the copy, and replace the old one.

**32. ****Promise.all()**

If your React app needs data from three different APIs, don't await them one by one. Run them concurrently.

JavaScript

const [users, posts] = await Promise.all([fetchUsers(), fetchPosts()]);

**33. ****setTimeout**** and Cleanups**

Executes code after a delay. In React (inside useEffect), if a component is removed from the screen before the timer finishes, it causes memory leaks. You must understand clearTimeout().

## **PART 4: The Ultimate JS vs Java Cheatsheet**

| **JavaScript Syntax / Concept** | **Code Snippet** | **Java Equivalent** |
| --- | --- | --- |
| **Constants** | const max = 100; | final int max = 100; |
| **Variables** | let count = 0; | Standard types (int count = 0;) |
| **Strict Equality** | if (x === 5) | == (primitive) or .equals() (object) |
| **Arrow Functions** | const add = (a, b) => a + b; | Lambdas: (a, b) -> a + b |
| **Object Maps** | const map = { a: 1 }; | HashMap<String, Integer> map = new HashMap<>(); |
| **Destructuring** | const { name } = user; | No direct equivalent; requires getters. |
| **Spread Operator** | const newArr = [...oldArr, 4]; | Arrays.copyOf() or looping. |
| **String to Number** | parseFloat("10.5") | Double.parseDouble("10.5"); |
| **NaN Check** | isNaN(value) | Catching NumberFormatException |
| **Async Request** | await fetch(url); | HttpClient, multithreading, CompletableFuture |
| **Array Transform** | arr.map(x => x * 2); | Stream.map() |
| **Array Filter** | arr.filter(x => x > 0); | Stream.filter() |
| **Array Check Any** | arr.some(x => x === 1); | Stream.anyMatch() |
| **Module Export** | export const num = 5; | public static final int NUM = 5; |
| **String Interpolation** | `Hello ${name}` | String.format("Hello %s", name) |
| **Null Fallback** | val = maybeNull ?? "Default" | Optional.orElse("Default") |

Feature list & Workflow

### **🌟 Part 1: Exhaustive Feature Architecture**

#### **1. Form Elements ****&**** Question Types (The Building Blocks)**

### Reform supports a massive array of data collection types. These can be pre-configured by the Builder (Static) or generated on-the-fly by the AI (Dynamic).

### **Standard Inputs:** Short Text (Names, single words), Long Text/Paragraph (Stories, explanations), Email, Phone Number (with country code auto-detect), URL.

### **Selection Inputs:** Multiple Choice (Radio buttons), Checkboxes (Multi-select), Dropdown Menus, Image-Choice (Clickable picture grids).

### **Quantitative Inputs:** Rating Scales (1-5 stars, 1-10 numbers), Opinion Sliders (Sliding scale from "Strongly Disagree" to "Strongly Agree"), NPS (Net Promoter Score).

### **Complex Inputs:** Date/Time Picker, Matrix/Grid (Rate multiple items on the same scale), Signature Pad (Draw to sign).

### **File ****&**** Media Uploads:** Standard File Dropzones supporting Images (.png, .jpg), Documents (.pdf, .docx, .txt), and Audio/Video snippets.

### **The AI Conversational Block (The Brain):** A special modular block that hands control over to the Gemini AI Engine to conduct a fluid, dynamic interview based on a core prompt.

#### **2. The Omni-Modal AI Conversational Engine**

### **Tri-Modal Input Sync:** End-users can seamlessly switch between speaking into their microphone, typing on their keyboard, or clicking dynamically generated UI widgets on their screen.

### **Real-Time Live Transcript (Speech-to-Text ****&**** Text-to-Speech):** As the AI speaks, its words are typed out on the screen in a chat-bubble UI. As the user speaks, their words are instantly transcribed and displayed.

### **Dynamic UI Generation:** The AI does not just output text. If the AI determines a multiple-choice question is best, it streams a JSON schema to the frontend, causing [Option A] and [Option B] buttons to elegantly fade onto the screen below the chat transcript.

### **Contextual File Prompts (Dynamic Dropzones):** If the AI determines it needs a document (e.g., "Could you provide a copy of your ID?"), it triggers a UI event. A sleek File Upload pop-up or inline dropzone dynamically materializes on the screen.

### **Live Document/Vision Analysis (Gemini Vision):** Once a user drops a PDF or picture into the chat, the AI reads it instantly and continues the conversation *based on the file's contents*.

### **Multi-Language Auto-Detect:** The AI detects the spoken/typed language of the user, replies audibly in that language, and translates all on-screen UI buttons instantly.

#### **3. Authentication, Roles, ****&**** Workspace Management**

### **Multi-Provider Auth:** JWT-based login via Email/Password, Google OAuth2, and Facebook OAuth2.

### **Role-Based Access Control (RBAC):** ADMIN (Billing/Settings), CREATOR (Builds forms), VIEWER (Can only see analytics).

### **Workspace Silos:** Agencies can create infinite Workspaces (e.g., "Client A", "Client B"). Data, forms, and webhooks are strictly isolated per workspace.

#### **4. Billing, Credits ****&**** Enterprise Monetization**

### **Stripe Subscription Tiers:** e.g., Free (Basic forms), Pro (AI Voice features), Enterprise (Headless API access).

### **Credit Ledger System:** AI interactions cost credits. Users buy credit packages. Real-time balance checks prevent abuse.

### **Credit Consumption Dashboard:** Visual breakdown of which forms/workspaces are burning the most AI credits.

#### **5. Form Publishing ****&**** Distribution**

### **Hosted Public URLs:** One-click links (reform.app/f/my-form).

### **Custom Domains (CNAME):** Map forms to survey.client-website.com.

### **Iframe ****&**** Popup Embedding:** Copy-paste HTML snippets to embed the form natively on Webflow, WordPress, Shopify, or trigger it as an exit-intent popup.

### **Headless JSON API:** Enterprise feature. Clients fetch the raw AI branching logic via API and render the visual form on their own entirely custom frontend framework.

#### **6. Analytics ****&**** Data Actionability**

### **Raw Submissions Data Table:** Traditional grid view of all collected data.

### **Playback Transcript Viewer:** Re-read the entire AI conversation, and click "Play" to listen to the raw audio recording of the user.

### **AI Auto-Summarization:** A button that condenses a 20-minute chat transcript into 3 actionable bullet points.

### **Sentiment ****&**** Thematic Tagging:** AI automatically tags submissions as "Positive", "Urgent", or "Frustrated", allowing creators to filter thousands of responses instantly.

### **Data Visualization:** Auto-generated pie charts and bar graphs for all quantitative/selection inputs.

#### **7. Enterprise API ****&**** Integrations**

### **Event-Driven Webhooks:** Fire JSON payloads to custom URLs instantly upon form completion.

### **Native Integrations:** One-click OAuth connections to HubSpot, Salesforce, Slack, and Google Sheets.

### **API Key Management:** Generate secure keys to pull submission data programmatically into internal company dashboards.

### **🔄 Part 2: In-Depth Workflows**

#### **Workflow A: The Form Builder (Your Client/Creator)**

### **Phase 1: Setup ****&**** Conception**

### **Login:** John logs in via Google Auth. He accesses his "HR Recruitment" Workspace.

### **Creation Choice:** He clicks "New Form". He chooses a **Hybrid Flow** (combining standard inputs with an AI interview).

### **Building the Static Layer:** Using the drag-and-drop canvas, he adds a Short Text block for "First Name", an Email block, and a File Upload block for "Resume". These are mandatory static fields.

### **Phase 2: Configuring the AI Brain**

### **Adding the AI Block:** John drags the "Conversational AI Engine" block below the static fields.

### **Setting the Persona ****&**** Prompt:** He types instructions into the AI setup box: *"**You are an expert technical recruiter. Review the resume they uploaded. Ask them 3 technical questions about the programming languages listed on their resume. Keep a professional but encouraging tone.**"*

### **Model Selection:** He switches the model from *Gemini Flash* to *Gemini Pro* because he needs the AI to deeply analyze the uploaded resume.

### **Guardrails ****&**** Constraints:** He toggles "Enable Dynamic File Requests" to ON, allowing the AI to ask for a portfolio link or PDF if the user mentions one. He sets a hard limit: "Max 5 questions total."

### **Phase 3: Publishing ****&**** Analysis**

### **Publishing:** John clicks "Publish", generates an embed code, and pastes it into his company's careers page.

### **Webhook Setup:** He configures a webhook: *"**When AI tags sentiment as 'Highly Qualified', push data to Slack channel #hiring.**"*

### **Reviewing:** Days later, John logs in. He views the Dashboard. He clicks on a specific candidate's transcript, reads the dynamically generated questions the AI asked, and listens to the candidate's audio responses.

#### **Workflow B: The Form Filler (The End-User / Customer)**

### **Phase 1: The Static Entry**

### **Arrival:** Sarah, an applicant, visits the careers page on her laptop. She sees the embedded Reform form.

### **Standard Input:** She types her name, her email, and drops her PDF resume into the standard static File Dropzone. She clicks "Begin Interview".

### **Phase 2: The AI Takeover (The Omni-Modal Experience)**

### **Visual Transition:** The screen seamlessly transitions. The background slightly blurs, bringing focus to a sleek chat interface in the center of the screen. A gentle audio chime plays.

### **The AI Speaks:** From her computer speakers, a warm, professional voice says: *"**Hi Sarah, thanks for applying. I see you have 3 years of experience in Java. Could you tell me about the most challenging Java project you've worked on?**"*

### **Live Transcript Rendering:** As the AI speaks, its words smoothly type out on the screen in an AI-chat bubble.

### **User Response (Tri-Modal):** Sarah can type her answer, but she prefers to speak. She holds the on-screen mic button (or spacebar) and says: *"**I built a modular monolith architecture for an e-commerce platform.**"* As she speaks, her words are transcribed instantly onto the screen in a User-chat bubble.

### **Phase 3: Dynamic UI ****&**** Contextual Intelligence**

### **Dynamic Options Fade In:** The AI analyzes her audio instantly. The AI speaks: *"**A modular monolith is a great choice. Why didn't you choose Microservices right away?**"*

### *Simultaneously*, the AI streams JSON instructions to the frontend. Below the chat text, three clickable UI buttons smoothly fade in: [Cost/Overhead], [Team Size], [Time to Market], along with an [Other] text box.

### **Clicking vs Speaking:** Sarah doesn't want to speak this time. She simply clicks the [Cost/Overhead] button on her screen. The system accepts this instantly.

### **Dynamic File Request:** The AI processes this and replies: *"**Makes complete sense. You mentioned earlier you designed the architecture. Do you happen to have a PDF of the architecture diagram you could share?**"*

### **The Dynamic Dropzone:** As the AI asks this, a visually distinct **"****Upload Architecture Diagram****"** modal dynamically slides up on her screen, featuring a drag-and-drop zone.

### **Multimodal Analysis:** Sarah drags a PDF into the dropzone. A quick loading animation spins. *Gemini Vision* reads the diagram. The AI speaks: *"**I see you used Spring Boot and PostgreSQL. Very solid setup. We use the same stack here.**"*

### **Phase 4: Wrap Up ****&**** Finalization**

### **Conclusion:** The AI reaches the 5-question limit set by the Builder. The AI says: *"**This has been fantastic, Sarah. Our team will review this and get back to you by Friday. Have a great day!**"*

### **Completion State:** The chat interface fades out into a beautifully animated "Success / Thank You" screen.

### **Background Action:** The connection securely closes. The full JSON tree of the conversation, the raw audio files, and the extracted data are saved to the PostgreSQL database, and the Webhook fires to the HR team's Slack.

Folder Structure

This is the **ultra-comprehensive, production-grade architecture** for **Reform**.

This structure is designed for **High Availability (HA)**, **Horizontal Scalability**, and **Enterprise Security**. It implements a **Domain-Driven Modular Monolith** that is ready to be broken into microservices if you ever reach millions of users, but is optimized for "thousands of users" using **Redis for distributed state** and **Load Balancing logic**.

### **📁 Reform Backend: Full Structural Blueprint**

codeText

📁 Reform Backend: Decoupled Enterprise Architecture

src/main/java/com/reform/app

├── 📁 core                          # SHARED KERNEL: Cross-cutting, universally used logic

│   ├── 📁 config                    # Global Infrastructure Configuration

│   │   ├── 📄 AsyncConfig.java             # Thread pools for Webhooks & AI background tasks

│   │   ├── 📄 WebSocketConfig.java         # STOMP / Raw WS for the Omni-Modal engine

│   │   ├── 📄 RedisCacheConfig.java        # Distributed cache setup

│   │   └── 📄 OpenApiConfig.java           # Swagger docs for the Enterprise Headless API

│   ├── 📁 domain                    # Base DDD (Domain-Driven Design) constructs

│   │   ├── 📄 BaseEntity.java              # Abstract: UUIDs, CreatedAt, UpdatedAt, @Version

│   │   └── 📄 JsonbConverter.java          # Maps Postgres JSONB to Jackson JsonNode

│   ├── 📁 exception                 # Centralized Error System (KISS: One way to handle errors)

│   │   ├── 📄 GlobalExceptionHandler.java  # Catches all, returns RFC-7807 Error Details

│   │   ├── 📄 RateLimitException.java      # HTTP 429 handler

│   │   └── 📄 InsufficientCreditsException.# Prevents AI execution if ledger is empty

│   └── 📁 event                     # Internal Observer Pattern (Decoupling domains)

│       ├── 📄 IDomainEvent.java            # Interface for all system events

│       └── 📄 EventPublisherImpl.java      # Spring ApplicationEventPublisher wrapper

│

├── 📁 auth                          # AUTHENTICATION & SECURITY

│   ├── 📁 config                    

│   │   └── 📄 SecurityConfig.java          # Route permissions, CORS, OAuth2 registration

│   ├── 📁 filter                    

│   │   └── 📄 JwtAuthenticationFilter.java # Intercepts and validates JWTs per request

│   ├── 📁 port                      # INTERFACES: Decoupling auth logic

│   │   ├── 📄 IAuthService.java            # Contract for login/register/tokens

│   │   └── 📄 ITokenProvider.java          # Contract for generating/validating tokens

│   ├── 📁 service                   # IMPLEMENTATIONS

│   │   ├── 📄 AuthServiceImpl.java         # Concrete auth logic (Implements IAuthService)

│   │   ├── 📄 JwtProviderImpl.java         # Concrete JWT logic (Implements ITokenProvider)

│   │   └── 📄 CustomUserDetailsService.java# Spring Security integration

│   ├── 📁 controller                

│   │   └── 📄 AuthController.java          # REST endpoints for login/oauth

│   └── 📁 dto                       

│       ├── 📄 AuthResponseDTO.java         # Immutable records for data transfer

│       └── 📄 LoginRequestDTO.java

│

├── 📁 user                          # IDENTITY & MULTI-TENANCY (RBAC & Workspaces)

│   ├── 📁 entity

│   │   ├── 📄 User.java                    # Root entity: Identity, preferences

│   │   ├── 📄 Role.java                    # Enum: ADMIN, CREATOR, VIEWER

│   │   └── 📄 Workspace.java               # Logical boundary for multi-tenancy

│   ├── 📁 repository                

│   │   ├── 📄 UserRepository.java          # Spring Data JPA interface

│   │   └── 📄 WorkspaceRepository.java     

│   ├── 📁 port                      # INTERFACES (SOLID: Interface Segregation)

│   │   ├── 📄 IUserService.java            # User CRUD contracts

│   │   └── 📄 IWorkspaceService.java       # Workspace isolation contracts

│   ├── 📁 service                   

│   │   ├── 📄 UserServiceImpl.java         # Implements IUserService

│   │   └── 📄 WorkspaceServiceImpl.java    # Implements IWorkspaceService, handles team invites

│   └── 📁 controller

│       ├── 📄 UserController.java          # User profile endpoints

│       └── 📄 WorkspaceController.java     # Workspace management endpoints

│

├── 📁 billing                       # REVENUE, CREDITS & STRIPE LEDGER

│   ├── 📁 entity

│   │   ├── 📄 CreditLedger.java            # Immutable event-sourced credit history

│   │   ├── 📄 SubscriptionPlan.java        # Tiers: FREE, PRO, ENTERPRISE

│   │   └── 📄 StripeCustomer.java          # Maps internal UUID to Stripe cus_XXX

│   ├── 📁 port                      # INTERFACES (SOLID: Dependency Inversion)

│   │   ├── 📄 IPaymentGateway.java         # Agnostic contract (Stripe, PayPal, etc.)

│   │   └── 📄 ICreditManager.java          # Contract for thread-safe credit burning

│   ├── 📁 service                   # IMPLEMENTATIONS

│   │   ├── 📄 StripeAdapterImpl.java       # Integrates Stripe SDK (Implements IPaymentGateway)

│   │   └── 📄 CreditManagerImpl.java       # Atomic DB updates for credits (Implements ICreditManager)

│   ├── 📁 controller

│   │   ├── 📄 BillingController.java       # API to upgrade plans

│   │   └── 📄 StripeWebhookController.java # Listens to async payment_success events

│   └── 📁 event

│       └── 📄 CreditDeductedEvent.java     # Fired after successful AI task

│

├── 📁 ai                            # THE BRAIN: Omni-modal Gemini Orchestration

│   ├── 📁 strategy                  # STRATEGY PATTERN (SOLID: Open/Closed)

│   │   ├── 📄 IAIEngineStrategy.java       # Interface for all AI Models

│   │   ├── 📄 GeminiFlashStrategy.java     # Fast/Voice sync model adapter

│   │   ├── 📄 GeminiProStrategy.java       # Complex reasoning model adapter

│   │   └── 📄 GeminiVisionStrategy.java    # Multimodal (Image/PDF) adapter

│   ├── 📁 port                      # INTERFACES

│   │   ├── 📄 IAIOrchestrator.java         # Main entry point for forms to request AI processing

│   │   └── 📄 IPromptBuilder.java          # Contract for dynamic prompt construction

│   ├── 📁 memory                    # DISTRIBUTED CONTEXT

│   │   ├── 📄 IConversationMemory.java     # Interface for chat history storage

│   │   └── 📄 RedisMemoryStoreImpl.java    # Redis implementation for fast context retrieval

│   ├── 📁 service                   

│   │   ├── 📄 AIModelFactory.java          # Factory Pattern: Yields correct strategy based on task

│   │   └── 📄 AIOrchestratorImpl.java      # Checks Credits -> Calls Factory -> Deducts Credits

│   └── 📁 mapper

│       ├── 📄 SchemaGenerator.java         # Translates AI intent into dynamic frontend JSON UI

│       └── 📄 AIResultMapper.java          # Structs raw LLM output into Java DTOs

│

├── 📁 form                          # DESIGNER ENGINE: Structure & Metadata

│   ├── 📁 entity

│   │   ├── 📄 Form.java                    # Form metadata, Theme, Workspace ID

│   │   └── 📁 block                        # POLYMORPHISM (SOLID: Liskov Substitution)

│   │       ├── 📄 IFormBlock.java          # Base interface for all blocks

│   │       ├── 📄 AbstractBlock.java       # Shared fields (id, order, isRequired)

│   │       ├── 📄 StaticBlock.java         # Text, Email, Rating

│   │       └── 📄 ConversationalBlock.java # Contains AI prompt, max limits, persona

│   ├── 📁 repository

│   │   └── 📄 FormRepository.java          # Cached lookup for public form rendering

│   ├── 📁 port                      

│   │   ├── 📄 IFormBuilderService.java     # Contract for Creator operations

│   │   └── 📄 IFormRenderingService.java   # Contract for End-User rendering (Read-only)

│   ├── 📁 service                   

│   │   ├── 📄 FormBuilderServiceImpl.java  # CRUD for Next.js Canvas Builder

│   │   └── 📄 FormRenderingServiceImpl.java# High-performance fetching for runtime

│   └── 📁 controller

│       ├── 📄 BuilderController.java       # Protected APIs for Creators

│       └── 📄 PublicRenderController.java  # Public endpoints for reform.app/f/{id}

│

├── 📁 submission                    # EXECUTION ENGINE: The Omni-Modal Sync

│   ├── 📁 entity

│   │   ├── 📄 Submission.java              # Final completed payload (JSONB)

│   │   └── 📄 TranscriptLine.java          # Individual spoken/typed dialogue records

│   ├── 📁 websocket                 # REAL-TIME LAYER

│   │   ├── 📄 IOmniModalHandler.java       # Interface for WS handlers

│   │   ├── 📄 VoiceSyncWSHandler.java      # Handles Audio buffer streaming in/out

│   │   └── 📄 UIGenerationWSHandler.java   # Streams JSON schema to frontend for dynamic buttons

│   ├── 📁 port                      

│   │   ├── 📄 ISubmissionProcessor.java    # Contract to finalize and validate form data

│   │   └── 📄 ITranscriptService.java      # Contract for live STT/TTS orchestration

│   ├── 📁 service                   

│   │   ├── 📄 SubmissionProcessorImpl.java # Validates final payload, saves to DB, fires Webhooks

│   │   └── 📄 LiveTranscriptServiceImpl.java # Connects WS audio buffers to AI Engine

│   └── 📁 controller

│       └── 📄 RESTSubmissionController.java# Fallback for standard non-AI static forms

│

├── 📁 storage                       # MEDIA ASSETS: Cloud Storage (Hexagonal Port/Adapter)

│   ├── 📁 port                      # INTERFACES

│   │   └── 📄 ICloudStoragePort.java       # Contract: upload(), generateSignedUrl()

│   ├── 📁 adapter                   # IMPLEMENTATIONS (Easily swap AWS for GCP)

│   │   ├── 📄 S3StorageAdapterImpl.java    # AWS S3 logic

│   │   └── 📄 GCSStorageAdapterImpl.java   # Google Cloud Storage logic (Fallback)

│   └── 📁 service

│       └── 📄 MediaUploadService.java      # Validates MIME types, scans for viruses, calls Port

│

├── 📁 analytics                     # INSIGHTS: Post-Processing & Dashboards

│   ├── 📁 port

│   │   ├── 📄 ISentimentAnalyzer.java      # Contract for evaluating submission mood

│   │   └── 📄 IDataAggregator.java         # Contract for generating chart data

│   ├── 📁 service

│   │   ├── 📄 GeminiSentimentImpl.java     # Async AI tagging (Positive, Urgent, etc.)

│   │   └── 📄 SQLDataAggregatorImpl.java   # Generates JSON for Pie/Bar charts

│   └── 📁 controller

│       └── 📄 AnalyticsController.java     # Serves the Creator Dashboard

│

└── 📁 enterprise                    # B2B EXTERNAL: Headless API & Webhooks

    ├── 📁 entity

    │   ├── 📄 ApiKey.java                  # Hashed enterprise keys

    │   └── 📄 WebhookConfig.java           # URL, Secret, Retry Count

    ├── 📁 port                      # INTERFACES

    │   ├── 📄 IWebhookDispatcher.java      # Contract for outgoing HTTP events

    │   └── 📄 IExternalSyncPort.java       # Contract for CRM integrations

    ├── 📁 adapter                   # IMPLEMENTATIONS

    │   ├── 📄 HubSpotSyncAdapter.java      # Specific logic for HubSpot API

    │   └── 📄 SalesforceSyncAdapter.java   # Specific logic for Salesforce API

    ├── 📁 service

    │   ├── 📄 WebhookDispatcherImpl.java   # Async processor with Exponential Backoff

    │   └── 📄 IntegrationManagerService.java # Manages OAuth handshakes for 3rd parties

    └── 📁 controller

        ├── 📄 HeadlessAPIController.java   # B2B raw JSON access

        └── 📄 IntegrationController.java   # Setup endpoints for HubSpot/Slack OAuth

### **🧠 Key Logic Explanations**

#### **1. The ****ai/strategy**** ****&**** ****ai/service**** (The Dynamic Brain)**

- **Why Strategy?** You have 3 Gemini models (Flash, Pro, Vision). Instead of writing if/else everywhere, the AIOperatorFacade asks the AIModelFactory for the right model based on the user's plan and the current task (e.g., "Is there a file in the request? Use Vision").

- **PromptTemplateManager:** This stores the "Expert Recruiter" or "Friendly Support" personas. It ensures the AI behavior is consistent and secure (guardrails).

#### **2. The ****submission**** (Omni-Modal WebSockets)**

- **ConversationalWSHandler**: This is the heart of the "Form Filler" experience. It maintains a stateful connection.

- **Input:** It receives a chunk of **Audio** or a **Button Click** from the user.

- **Processing:** It passes it to the AIOperatorFacade.

- **Output:** It pushes back a LiveFrameDTO which contains the AI's **Audio response**, the **Transcript text**, and the **UI Schema JSON** (telling the Next.js frontend to show specific buttons).

#### **3. The ****billing**** (Credit Ledger)**

- **No simple counters:** We use a CreditLedger (Entity). This records id, user_id, amount, reason (SPENT_ON_AI, BOUGHT_VIA_STRIPE).

- This is critical for "thousands of users" so you can audit exactly why a user's balance is zero.

#### **4. The ****form/entity/block**** (Question Types)**

- Supports the "Massive Array" of questions.

- **ConversationalBlock**: Unlike a StaticBlock which just has a label (e.g., "Name"), the ConversationalBlock contains the AI prompt and the credit cost settings.

#### **5. The ****enterprise**** (Headless ****&**** Webhooks)**

- **HeadlessAPIController**: Satisfies the "Enterprise API" requirement. It doesn't return HTML; it returns the "Brain" of the form as a JSON schema, allowing an agency to use your AI but their own visual design.

- **WebhookDispatcher**: Listens for the SubmissionCompletedEvent. It works in a background thread (AsyncConfig) so that the end-user doesn't have to wait for your app to talk to Salesforce before they see the "Thank You" screen.

### **🧠 System Design ****&**** ****"****Big Words****"**** Explained**

#### **1. Rate Limiting (The ****"****Bouncer****"****)**

- **Where:** core/interceptor/RateLimitInterceptor.java.

- **How:** It uses **Redis + Bucket4j**. Every time a user speaks to the AI, the app checks Redis. if a user (by IP or UserID) exceeds 10 requests per minute, it blocks the request with a 429 Too Many Requests. This prevents your AI costs from exploding if a bot attacks you.

#### **2. Distributed State ****&**** Session Tracking (The ****"****Scale-out****"****)**

- **Where:** submission/state/SessionTracker.java.

- **How:** Because you have "thousands of users," you will eventually run 2 or 3 copies of your Spring Boot app behind a **Load Balancer**.

- **Problem:** If Sarah starts a WebSocket chat on Server A, but her next message is routed to Server B, Server B won't know her history.

- **Solution:** We store the ConversationHistory and ActiveSessionMetadata in **Redis**. Now, any server can pick up the conversation where it left off.

#### **3. Cloud Storage (The ****"****Infinite Hard Drive****"****)**

- **Where:** storage module.

- **How:** You never store audio files or images in your PostgreSQL database. The app streams the audio from the user, sends it to the CloudStorageService (AWS S3), and only stores the **URL link** in the database.

#### **4. The ****"****Headless****"**** BFF Pattern (Business for Frontend)**

- **Where:** enterprise/controller/HeadlessAPIController.java.

- **How:** High-end clients (like a bank) don't want to use your UI. They use this API to get the **"****Brain****"** (the logic). Your API tells their app: *"**The AI says ask Question 2 now, and show a multi-select button.**"*

#### **5. Database Migrations (The ****"****Time Machine****"****)**

- **Where:** src/main/resources/db/migration (using **Flyway**).

- **How:** Every time you change the database (add a column to User), you write a small SQL file (V1__init.sql, V2__add_credits.sql). When the app starts, it checks these files and updates the DB. This is how you manage a database for thousands of users without ever losing data.

#### **6. Mappers (The ****"****Translator****"****)**

- **Where:** mapper folders in every module.

- **How:** We use **MapStruct**. It automatically converts your "Database Entity" (private data) into a "DTO" (public data). This ensures you never accidentally send a user's hashed password or private API key to the frontend.

MVC

Model view controler

Pages

### **🌐 Group 1: Public ****&**** Authentication (The Entry)**

#### **1. Landing Page (The ****"****Hook****"****)**

- **Purpose:** High-conversion showcase of Omni-modal technology.

- **Key Components:**

- **Live Interactive Hero:** A "Try Me" section where visitors can click a mic and talk to an AI that instantly renders a "Contact Us" button on the screen.

- **Value Props:** "Talk to build," "Visual-Voice Sync," and "Enterprise API Ready."

- **Dynamic Pricing Table:** Integrated with Stripe tiers (Free, Pro, Enterprise).

#### **2. Authentication Portal**

- **Purpose:** Frictionless entry via JWT and OAuth2.

- **Key Components:**

- **Social Auth Buttons:** "Continue with Google," "Continue with Facebook."

- **Magic Link Entry:** For passwordless email login.

- **Onboarding Step:** A one-time screen asking, "What is your workspace name?" and "Invite your team."

### **🛠️ Group 2: The Creator Command Center (Internal Dashboard)**

#### **3. Main Dashboard (Home)**

- **Purpose:** High-level overview of form performance and credit health.

- **Key Components:**

- **Credit Status Bar:** A real-time balance of AI credits (linked to the billing module).

- **Form Activity Grid:** Cards showing total submissions, completion rates, and an "AI Sentiment" sparkline.

- **Global Actions:** Big "✨ Create with AI" and "🛠️ Build Manually" buttons.

#### **4. The Builder Studio (Dual-Mode Engine)**

- **Purpose:** The core workspace for form creation.

- **Page View A: Visual Canvas:**

- **Left Panel:** Draggable blocks (Short Text, Rating, AI Conversational Block).

- **Center Canvas:** A timeline of the form.

- **Right Settings Panel:** AI Persona config (Tone: Friendly/Professional), AI Model Switcher (Gemini Flash vs. Pro), and Credit usage limits.

- **Page View B: Conversational Builder (AI Overlay):**

- **Experience:** The canvas blurs. A massive audio visualizer appears.

- **Interaction:** John (Builder) says: *"**Add a question asking for their budget, then show a slider.**"*

- **Live Preview:** The AI replies audibly and physically "drops" the slider block onto the blurred canvas in real-time.

#### **5. Analytics ****&**** AI Insights (Per Form)**

- **Purpose:** Turning unstructured voice/chat into actionable data.

- **Sub-Tabs:**

- **Tab 1: Normalized Data:** Standard charts for Multiple Choice and Ratings.

- **Tab 2: AI Summaries:** A markdown section where Gemini Pro lists "Top 3 Customer Pain Points" based on all voice transcripts.

- **Tab 3: Response Inbox:** A list of every person who filled the form.

- **Tab 4: Transcript Playback Viewer:** A dedicated sub-page showing the chat bubbles. It includes an **Audio Player** to hear the user’s voice and a **Sentiment Tag** (e.g., "User was frustrated").

### **🔌 Group 3: Enterprise ****&**** System Settings**

#### **6. API ****&**** Integration Hub**

- **Purpose:** Management of the "Headless" engine and data pushing.

- **Key Components:**

- **API Key Manager:** Generate/Revoke X-API-KEY for backend integrations.

- **Webhook Developer Console:** A list of endpoint URLs with a "Test Webhook" button and recent logs (Success/Fail).

- **Headless BFF Docs:** A mini-playground showing the JSON schema that the enterprise module returns for that specific form.

- **Integrations:** Connect Salesforce, HubSpot, or Slack via OAuth buttons.

#### **7. Billing ****&**** Workspace Settings**

- **Purpose:** Managing the Stripe relationship and team roles.

- **Key Components:**

- **Stripe Portal Integration:** A button to "Manage Subscription" (handled by Stripe's hosted portal).

- **Credit Ledger Table:** A historical list: *"**1000 Credits Purchased**"*, *"**5 Credits Spent on 'HR Form'**"*.

- **Team Management:** Invite users and assign roles (ADMIN, CREATOR, VIEWER).

### **🎤 Group 4: The Experience (End-User View)**

#### **8. The Omni-Modal Form (The ****"****Face****"****)**

- **Purpose:** The interface the customer sees when they click a link.

- **Design:** Full-screen, high-focus, mobile-first.

- **Key UI Layers:**

- **The Transcript Area:** Top 70% of the screen. Animated chat bubbles for the AI and the User.

- **The AI Indicator:** A pulsing orb that changes color when the AI is "Listening" vs. "Speaking."

- **The Dynamic UI Slot:** A central area where the Spring Boot backend injects UI components (e.g., a File Dropzone, a Rating Scale, or Multiple Choice buttons).

- **The Tri-Modal Dock:** A fixed bottom bar with:

- A prominent **Microphone Button** (Voice).

- A small **Keyboard Icon** (to expand Text input).

- Visual indicators for the user to "Click" the buttons rendered in the Dynamic UI Slot.

#### **9. Success / Thank You Page**

- **Purpose:** Graceful exit and data confirmation.

- **Key Components:**

- Custom message set by the Builder.

- "Download My Summary" button (Enterprise feature: AI generates a summary for the user themselves).

- Automatic redirect to the Client's website after 5 seconds.

### **📁 Mapping Pages to Spring Boot Modules**

| **Frontend Page** | **Backend Module (Spring Boot)** | **Data Flow** |
| --- | --- | --- |
| **Landing/Auth** | auth, user | JWT issuance & OAuth profile sync. |
| **Dashboard** | form, billing | Fetch user forms & credit ledger balance. |
| **Builder Studio** | form, ai | Send prompt to AIOperatorFacade; save to FormRepository. |
| **Analytics** | analytics, submission | Fetch Submission JSONB; trigger GeminiProStrategy for summary. |
| **API Hub** | enterprise | Manage ApiKey entities & WebhookConfig. |
| **The Form Filler** | submission, ai | **Websocket Connection**: Streams audio to AI; receives UI Schema. |
| **Billing/Stripe** | billing | Redirect to Stripe; listen for Webhook to update CreditLedger. |

MVP

The goal of the MVP (Minimum Viable Product) for **Reform** is to prove the core value proposition: **"****An AI that can conduct a synchronized voice and visual interview.****"**

You should not build all 50+ features at once. Instead, focus on the "Happy Path" where a user creates a form, an end-user fills it using voice/clicks, and the creator sees the result.

### **🎯 The MVP Definition**

The MVP is a **web-based Omni-modal Form Engine** that allows a creator to build a form with at least one **AI Conversational Block** and allows an end-user to complete that form using **Voice-Visual Sync** (hearing the AI and clicking dynamic buttons).

### **🔴 1. The MVP Feature List (Must-Haves)**

#### **A. Core Omni-Modal Engine (The ****"****Magic****"****)**

- **Bi-directional WebSockets:** Stable connection for streaming audio from the user and streaming JSON (UI) + Audio (AI Voice) back.

- **Gemini 3.1 Flash Integration:** Using the "Flash" model for low-latency responses.

- **Speech-to-Text (STT) ****&**** Text-to-Speech (TTS):** The user sees their words typed out, and hears the AI speak.

- **Basic Dynamic UI:** The AI must be able to trigger at least two UI types: Multiple Choice Buttons and Short Text Input.

#### **B. Simplified Form Builder**

- **Manual Block Addition:** Drag-and-drop is too complex for MVP. Use a simple "Add Block" button.

- **AI Persona Prompting:** A text area where the creator defines: "You are a [Recruiter/Salesman]" and "Your goal is to [Collect a name]".

- **Basic Block Types:** Support for Short Text, Email, and the Conversational AI Block.

#### **C. Essential Infrastructure**

- **JWT ****&**** Google Auth:** Secure login for creators.

- **Credit Check (Hard Gate):** A simple balance check. If credits = 0, the AI block won't start. (Crucial to prevent your Google Cloud bill from exploding).

- **Public Hosted Link:** A unique URL (e.g., /f/{id}) that any end-user can visit.

- **Submissions Table:** A simple grid showing the "Final Data" collected.

### **📊 2. MoSCoW Prioritization (Full Project Scope)**

| **Category** | **Must-Have (MVP)** | **Should-Have (V1.1)** | **Could-Have (Enterprise)** |
| --- | --- | --- | --- |
| **Question Types** | Short Text, Email, AI Block, Multiple Choice | Long Text, Rating (1-5), Date, File Upload | Signature, Matrix Grid, NPS, Image Choice |
| **AI Engine** | Voice-Visual Sync, Flash Model, Basic Context | Gemini Vision (Image analysis), Sentiment Tagging | Multi-language detection, Gemini Pro reasoning |
| **Auth ****&**** User** | Google Login, 1 Workspace per user | Team Invites, RBAC (Creator/Viewer roles) | Agency White-labeling, Microsoft/FB Auth |
| **Billing** | Credit Balance (Manual update), Hard-limit gate | Stripe Integration (Buy credits), Subscription tiers | Usage Analytics dashboard, Auto-recharge |
| **Distribution** | Public URL (reform.app/f/id) | Iframe Embed Code, Popup script | Custom CNAME Domains (forms.yourbiz.com) |
| **Enterprise** | Local DB Storage, Raw Transcript View | Webhooks (JSON POST), CSV Export | Headless API, Salesforce/HubSpot native sync |

### **🏗️ 3. Mapping the MVP to your Folder Structure**

Since you are "Learning by doing," here is which folders you need to code **first** to get the MVP running:

- **auth/**** ****&**** ****user/**: Get a user logged in so you have a userId.

- **form/**** (Basic)**: Create the Form and FormBlock entities. You only need StaticBlock and ConversationalBlock for now.

- **ai/**** (The Core)**: Implement the GeminiFlashStrategy. This is where the voice happens.

- **submission/**: Set up the ConversationalWSHandler. This is the most "Learning-intensive" part as it handles the real-time traffic.

- **core/**: Implement the JsonbConverter (to save the chat) and GlobalExceptionHandler.

### **🚀 MVP User Workflow (The ****"****Happy Path****"****)**

- **Creator:** Logs in -> Clicks "New Form" -> Adds an **AI Block** -> Types: *"**Ask the user for their favorite color and why.**"* -> Clicks "Publish" -> Copies Link.

- **Filler (End-User):** Opens link -> Clicks "Start" -> **AI Speaks:** *"**Hi! What's your favorite color?**"* -> **Filler clicks** a [Blue] button that appeared on screen -> **AI Speaks:** *"**Nice! Why do you like Blue?**"* -> **Filler speaks:** *"**It reminds me of the ocean.**"* -> Form Ends.

- **Creator:** Refreshes dashboard -> Sees 1 new submission -> Reads: Color: Blue, Reason: Reminds me of ocean.

### **🧠 Why this MVP?**

This MVP skips **Stripe** (you can manually add credits to your own account in the DB), skips **Webhooks** (you can look at the dashboard), and skips **Custom Domains**. It focuses entirely on the **Omni-modal technology**, which is the "Reason to buy" your software. Once the WebSocket streams audio and buttons correctly, the rest is just standard "SaaS" building.

MVP Sprint Plan

# **📄 REFORM MVP: 1-Month Engineering Sprint Plan**

**Goal:** Prove the "Reason to Buy" — an AI that conducts a synchronized voice and visual interview.
**Architecture:** Decoupled Modular Monolith (Ports & Adapters).
**Resources:** Hung (Architectural/Streaming focus), Thien (Domain/Data focus).

## **📅 Week 1: Infrastructure, Identity, ****&**** Static Forms**

**Objective:** Get the databases running via Docker, secure the API, and prove we can build a standard (non-AI) form.

### **👨‍💻 Thien's Tasks (Database, Data Layer, Static Builder)**

- **Docker DB:** Write the docker-compose.yml to spin up PostgreSQL.

- **Domain Models:** Create the core JPA entities. Focus on inheritance for the blocks (Base Block -> Static / Conversational).

- **Static Form CRUD:** Build the backend endpoints so a user can click "Add Text Block" and save the Form to the database.

- **🧠 Thien's ****"****Hard****"**** Challenge:** Implement the JsonbConverter so PostgreSQL can store dynamic block properties seamlessly without rigid columns.

- **Folders ****&**** Classes:**

- core/domain/BaseEntity.java, JsonbConverter.java

- form/entity/Form.java, block/IFormBlock.java, block/StaticBlock.java

- form/port/IFormBuilderService.java, form/service/FormBuilderServiceImpl.java

- form/controller/BuilderController.java

### **👨‍💻 Hung's Tasks (Security, Cache Infra, Auth CRUD)**

- **Docker Cache:** Update Thien's docker-compose.yml to include Redis.

- **Security Layer:** Implement the JWT filter and Spring Security config. Block unauthorized requests.

- **Auth System:** Implement standard Email/Password login (Google Auth can wait for V2).

- **🛠️ Hung's ****"****CRUD****"**** Task:** Build the User Profile and Workspace CRUD endpoints.

- **Folders ****&**** Classes:**

- auth/config/SecurityConfig.java, auth/filter/JwtAuthenticationFilter.java

- auth/port/IAuthService.java, auth/service/AuthServiceImpl.java

- user/entity/User.java, user/entity/Workspace.java

- user/controller/UserController.java (CRUD)

## **📅 Week 2: Submission Engine ****&**** Real-Time Piping**

**Objective:** Allow users to view/fill forms, and lay the WebSocket plumbing for the AI.

### **👨‍💻 Thien's Tasks (Public Rendering ****&**** Submissions)**

- **Public Link API:** Create the read-only endpoint that fetches a form by its slug (e.g., /f/my-form).

- **Static Submissions:** Build the REST API to receive a standard HTTP POST submission and save it to the DB.

- **Submissions Table CRUD:** Create the endpoint for the Creator to view a list of all their received submissions.

- **🧠 Thien's ****"****Hard****"**** Challenge:** Implement Redis caching (@Cacheable) on the Public Link API. If a form goes viral, it should read from Redis, not Postgres.

- **Folders ****&**** Classes:**

- form/controller/PublicRenderController.java, form/service/FormRenderingServiceImpl.java

- submission/entity/Submission.java (Using JSONB for answers)

- submission/controller/RESTSubmissionController.java

- core/config/RedisCacheConfig.java

### **👨‍💻 Hung's Tasks (WebSockets ****&**** State Management)**

- **WebSocket Setup:** Configure STOMP or Raw WebSockets in Spring Boot.

- **Session Management:** When a user connects via WebSocket, generate a Session ID and store their active state in Redis.

- **Rate Limiting:** Protect Thien's Public Link API and your WebSockets using Bucket4j (backed by Redis) to prevent DDoS attacks.

- **Folders ****&**** Classes:**

- core/config/WebSocketConfig.java

- submission/websocket/IOmniModalHandler.java, VoiceSyncWSHandler.java

- submission/state/SessionTracker.java (Redis integration)

- core/interceptor/RateLimitInterceptor.java

## **📅 Week 3: The Brain (Google AI Free Tier)**

**Objective:** Integrate Gemini 1.5 Flash. Thien will use it for text-based form generation, Hung will wire it for real-time voice conversations.

### **👨‍💻 Thien's Tasks (AI Form Builder - Chat-to-Build)**

- **AI Form Generation:** Build the endpoint where a creator types, *"**Make me a lead gen form,**"* and the AI returns the form structure.

- **Prompt Engineering:** Write the hidden system prompt that forces Gemini to output strict JSON arrays.

- **🧠 Thien's ****"****Hard****"**** Challenge:** Build the SchemaGenerator. You must take the raw JSON string from Gemini, parse it, catch "hallucinations," and map it into real StaticBlock Java objects to save to the DB.

- **Folders ****&**** Classes:**

- ai/port/IPromptBuilder.java

- ai/mapper/SchemaGenerator.java

- form/factory/BlockFactory.java

- form/controller/BuilderController.java (Add /generate endpoint)

### **👨‍💻 Hung's Tasks (AI Orchestration ****&**** WebClient)**

- **Gemini SDK Integration:** Set up the generic WebClient to talk to Google's Generative Language API (Bypass the heavy Vertex SDK to keep it free).

- **The AI Facade:** Create the AIOrchestratorImpl that takes user input, passes it to Gemini, and receives the response.

- **Live Context Memory:** Implement Redis-backed memory so Gemini remembers what it asked the user 2 minutes ago.

- **Folders ****&**** Classes:**

- ai/strategy/GeminiFlashStrategy.java

- ai/port/IAIOrchestrator.java, ai/service/AIOrchestratorImpl.java

- ai/memory/IConversationMemory.java, RedisMemoryStoreImpl.java

## **📅 Week 4: The Omni-Modal Sync (Closing the Loop)**

**Objective:** Merge the UI, the Voice, and the Data into the final "Magic" experience.

### **👨‍💻 Thien's Tasks (Finalizing Data ****&**** Credits)**

- **Credit System (Hard Gate):** Implement a simple check: Does the user have credits > 0? If no, throw InsufficientCreditsException before the AI starts.

- **Submission Processor:** When the AI chat finishes, take the complete conversation JSON tree from Hung's WebSocket session and save it permanently via SubmissionProcessorImpl.

- **MVP Polish:** Ensure the Creator Dashboard shows the exact transcript of the AI-to-User conversation.

- **Folders ****&**** Classes:**

- billing/port/ICreditManager.java, billing/service/CreditManagerImpl.java

- submission/port/ISubmissionProcessor.java, SubmissionProcessorImpl.java

- core/exception/InsufficientCreditsException.java

### **👨‍💻 Hung's Tasks (Dynamic UI WebSockets ****&**** Audio buffers)**

- **Omni-Modal DTOs:** Create the OmniModalFrameDTO. This is the packet that holds both the AI text/audio AND the UI JSON schema.

- **Dynamic UI Streaming:** Inside the WebSocket handler, when the AI asks a multiple-choice question, push a JSON schema to the frontend so buttons appear on the user's screen.

- **Audio Handling:** Write the utility that accepts raw audio buffers from the browser's microphone and prepares them for the STT (Speech-to-Text) pipeline.

- **Folders ****&**** Classes:**

- submission/dto/OmniModalFrameDTO.java

- submission/websocket/UIGenerationWSHandler.java

- core/util/AudioFormatUtils.java

## **🎯 Success Criteria for End of Month 1**

If you succeed, you will be able to do this live:

- **Hung** logs in, clicks "New Form", and types: *"**Ask the user for their favorite food.**"* (Thien's AI Schema logic builds the form).

- **Hung** copies the reform.app/f/123 link and sends it to **Thien**. (Thien's Redis Cache serves the link instantly).

- **Thien** opens the link. His WebSocket connects (Hung's logic). The AI says: *"**Hi, what's your favorite food?**"*

- **Thien** speaks: *"**Pizza**"* (Hung's Audio Buffer sends it to Gemini).

- Gemini responds, and the WebSocket sends a multiple-choice button to Thien's screen: [Pepperoni] [Cheese] [Veggie]. (Hung's UI schema logic).

- **Thien** clicks [Pepperoni]. The form ends.

- **Hung** refreshes his dashboard and sees: *Submission #1: Food: Pizza, Topping: Pepperoni.* (Thien's DB/Submission table logic).

Things to learn

### **🧠 Part 1: The ****"****Whys****"**** (The Architectural Decisions)**

#### **1. Why a ****"****Modular Monolith****"**** instead of Microservices?**

- **The Decision:** We put everything in one project but separated them into independent folders (Modules) like billing, ai, and form.

- **The Why:** Microservices are a nightmare for a solo developer or a small team (network latency, deployment complexity). A Modular Monolith gives you the **separation of concerns** (clean code) without the **operational overhead**. If "Billing" ever gets too big, you can just cut that folder out and move it to its own server later.

#### **2. Why Domain-Driven (Folder-by-Feature) instead of Layer-Driven (Folder-by-Type)?**

- **The Decision:** Grouping files by ai, billing, and user instead of having one giant controllers folder.

- **The Why:** In a "thousands of users" app, finding a bug in the billing logic is much faster if all billing files are in one place. It prevents the "Spaghetti Code" where every file depends on every other file.

#### **3. Why the Strategy Pattern for AI Models?**

- **The Decision:** Creating the IAIEngineStrategy interface.

- **The Why:** AI moves fast. Today it's Gemini 3.1. Tomorrow it's Gemini 4.0 or GPT-5. By using the Strategy pattern, you can add a new model by just adding one new file. The rest of your code (the "Form Filler" logic) never has to change. It makes your app **future-proof**.

#### **4. Why the Facade Pattern for the AI Operator?**

- **The Decision:** The AIOperatorFacade.

- **The Why:** Calling an AI involves checking credits, checking history, calling the API, and logging the result. If you put that logic inside the SubmissionService, that service becomes too "fat." The Facade provides a "One-Button" interface for the AI, hiding all the complexity.

#### **5. Why use Redis for Session ****&**** Rate Limiting?**

- **The Decision:** Adding RedisConfig and ActiveSessionCache.

- **The Why:** PostgreSQL is great for "permanent" data (your name, your form). Redis is for "temporary" data. It lives in the RAM (super fast). Since the AI conversation happens in real-time, checking the DB for every word the AI says is too slow. Redis makes it feel instant.

### **🗺️ Part 2: Your Step-by-Step Learning Path**

#### **Phase 1: The Foundations (Easy)**

- **What to learn:**

- **Java Basics:** Focus on Interfaces and Abstract Classes (you'll need these for the FormBlock logic).

- **Spring Boot REST:** How to make a basic Controller that returns "Hello World."

- **Maven/Gradle:** How to add dependencies (like Stripe or Google AI SDK).

- **Why now?** You can't build a spaceship without knowing how a screw works.

#### **Phase 2: Data ****&**** Persistence (Medium)**

- **What to learn:**

- **Spring Data JPA:** How to save a User to a database.

- **DTO Pattern:** Learn why we never return an Entity directly. Learn **MapStruct** to convert Entities to DTOs.

- **Postgres JSONB:** Research how to store "unstructured data" in SQL. This is the "secret sauce" for your dynamic forms.

- **Database Migrations (Flyway):** Learn how to version-control your database.

- **Why now?** Your app is useless if it forgets the forms your users built.

#### **Phase 3: Security ****&**** Identity (Medium)**

- **What to learn:**

- **Spring Security:** The filter chain (how a request is intercepted).

- **JWT (JSON Web Tokens):** How to log a user in without using a "Session."

- **OAuth2:** How "Login with Google" works under the hood.

- **Why now?** You are handling billing and private forms; security is non-negotiable.

#### **Phase 4: Real-time ****&**** Scale (Hard)**

- **What to learn:**

- **WebSockets:** Learn how a server can "push" a message to a browser without the browser asking for it (crucial for the AI's voice).

- **Redis:** Learn how to store a simple Key-Value pair and how "Rate Limiting" works.

- **Event-Driven Programming:** Learn how to use @EventListener. (e.g., When a form is finished, "publish" an event so the Webhook module can catch it).

- **Why now?** This is what separates a "student project" from a "professional product."

#### **Phase 5: The AI Orchestration (Pro)**

- **What to learn:**

- **The Strategy Pattern:** Deep dive into how to swap logic at runtime.

- **Gemini API/Vertex AI:** Learn about "System Instructions," "Temperature," and "Token counts."

- **Audio Buffers:** Learn how to handle Byte arrays in Java (for the voice recordings).

- **Why now?** This is your core business value.

Phase 1

# **🏛️ Phase 1: Architecture ****&**** System Design Master Guide**

**Project:** Reform (Omni-Modal AI Form Builder & API Engine)

**Version:** 2.1 (Voice-Visual Sync & Enterprise APIs)

**Date:** Late 2026

## **Part 1: Exhaustive System Requirements (The Rules of Execution)**

Before writing a single line of Java or TypeScript, we define exactly what the system must do and the strict engineering metrics it must survive.

### **1.1 Functional Requirements (FRs) - *****What it does***

- **FR1 (Dual Builder Engine):** The system must provide a standard drag-and-drop builder AND an immersive "Conversational Builder" where clients build forms by speaking to the AI and uploading reference images.

- **FR2 (Synchronized Voice-Visual Execution):** For end-users, the system must stream the AI's spoken voice aloud while simultaneously generating the corresponding text and interactive UI widgets (sliders, multiple choice) on the screen.

- **FR3 (Tri-Modal Input):** The end-user must be able to seamlessly switch between speaking (Microphone), typing (Text Input), and clicking (Generated UI widgets) to answer any question.

- **FR4 (Dynamic Branching):** The AI must calculate the next logical question entirely on the fly based on the previous multimodal input.

- **FR5 (Enterprise API Hub):** The system must expose Webhooks (Push), REST Endpoints (Pull), and a Headless BFF (Backend-for-Frontend) API for enterprise integrations.

- **FR6 (Background Intelligence):** The system must use Gemini 3.1 Pro asynchronously to cluster unpredictable dynamic answers into normalized charts and sentiment reports.

### **1.2 Non-Functional Requirements (NFRs) - *****How it survives***

- **NFR1 (Time-to-First-Audio - TTFA):** When a user finishes speaking, the first audio byte from Gemini 3.1 Flash must play on the user's speakers in **<**** 400ms**.

- **NFR2 (Persistent Bi-Directional Latency):** Because the frontend streams raw microphone buffers to the backend, the WebSocket latency must remain under 50ms to prevent buffer underruns and choppy audio.

- **NFR3 (Concurrency ****&**** Threading):** The Spring Boot backend must support **25,000+ simultaneous open WebSockets** without JVM thread exhaustion.

- **NFR4 (Storage Scalability):** Audio recordings of user answers must be piped directly to Cloud Storage (AWS S3/GCS) in chunks, never held completely in JVM memory.

## **Part 2: The Split-Domain ****&**** Infrastructure Strategy**

You cannot host a complex client dashboard, a real-time Voice AI, and high-volume Enterprise APIs on the same infrastructure mindset.

| **Layer** | **URL Strategy** | **Tech Stack ****&**** Rendering** | **Infrastructure Purpose** |
| --- | --- | --- | --- |
| **Marketing Site** | reform.com | Next.js (SSG) | Vercel Edge Network. Instant loads for SEO. |
| **Client Dashboard** | app.reform.com | Next.js (CSR) | Vercel. Hidden behind authentication. Highly interactive state management. |
| **Public Forms** | form.reform.com/[id] | Next.js (SSR + CSR WebRTC) | Vercel for initial HTML payload, transitioning immediately to WebSockets for the live Audio/UI stream. |
| **Omni-Modal AI Gateway** | ws.api.reform.com | Java Spring Boot 3.4+ (WebFlux) | AWS ECS (Fargate). Dedicated heavily-scaled clusters just for managing persistent WebSocket audio streams. |
| **Enterprise API Hub** | api.reform.com/v1 | Java Spring Boot 3.4+ (REST) | Handles high-throughput stateless CRUD requests from Enterprise devs pulling JSON data. |

## **Part 3: Domain-Driven Design (DDD) Blueprint**

To avoid the "Spaghetti Code" trap, your Spring Boot monolith must be strictly divided into **Bounded Contexts**. Code in one context cannot directly manipulate the database tables of another context.

### **Context 1: ****WorkspaceManagement**

- **Responsibility:** Auth, Clients, RBAC (Agency vs. Sub-client), Stripe Billing.

- **Database Tables:** users, workspaces, subscriptions, api_keys.

### **Context 2: ****FormCore**** ****&**** ****ApiEngine**

- **Responsibility:** Manages the schema of the forms, the static guardrails, and the Webhook dispatcher.

- **Database Tables:** forms, form_guardrails, webhooks.

- **Rule:** When a form completes, FormCore fires an internal event. ApiEngine listens and automatically POSTs the data to the client's registered Salesforce/Slack endpoints.

### **Context 3: ****OmniModalEngine**** (The Voice/Visual Engine)**

- **Responsibility:** Handles the live, bi-directional WebSocket connection. Connects to the **Google Gemini 3.1 Live API**. Manages incoming audio buffers and outgoing JSON (UI instructions) + Audio streams.

- **Database Tables:** conversations (JSONB Only).

- **Rule:** Strictly isolated. Once the AI determines the form goal is met, it sends an event back to FormCore to mark the session complete.

## **Part 4: The PostgreSQL JSONB Master Schema (Multimodal)**

Because Full Conversational Forms generate unpredictable branching, and users might answer via voice, text, or image uploads, rigid SQL columns will fail. You must use PostgreSQL **JSONB**.

SQL

- CREATE TABLE conversations (

-     session_id UUID PRIMARY KEY,         

-     form_id UUID NOT NULL,              

-     status VARCHAR(50) DEFAULT 'ACTIVE', 

-     turn_count INT DEFAULT 0,            

-     transcript JSONB NOT NULL,           -- The massive, dynamic JSON object

-     created_at TIMESTAMP DEFAULT NOW()

- );

- -- Crucial: Create a GIN Index so the Analytics engine can search unstructured data

- CREATE INDEX idx_conversations_jsonb ON conversations USING GIN (transcript);

**What goes inside the ****transcript**** JSONB column?**

Notice how it tracks the exact modality the user used, and the exact UI Gemini generated.

JSON

- {

-   "client_goal": "Get product feedback and a photo of any damage.",

-   "messages": [

-     { 

-       "role": "assistant", 

-       "spoken_text": "How would you rate your delivery?", 

-       "generated_ui": { "type": "rating_scale", "max": 5 }

-     },

-     { 

-       "role": "user", 

-       "modality": "click", 

-       "content": "3" 

-     },

-     { 

-       "role": "assistant", 

-       "spoken_text": "I see you gave 3 stars. Can you tell me what went wrong?", 

-       "generated_ui": { "type": "text_input" }

-     },

-     { 

-       "role": "user", 

-       "modality": "voice", 

-       "content": "[Transcribed] The box was crushed.",

-       "audio_url": "s3://reform-audio/session_123/turn_4.wav"

-     }

-   ]

- }

## **Part 5: The Real-Time WebSocket Architecture (Streaming Voice ****&**** UI)**

Because Gemini 3.1 Flash supports native Audio-to-Audio processing, we abandon standard HTTP Server-Sent Events (SSE). SSE is unidirectional (Server -> Client). We need **full-duplex WebSockets** so the user can speak while the AI is simultaneously sending back UI instructions.

### **The Next.js Frontend (The Media Handler)**

- **Audio Capture:** Uses the browser's MediaRecorder API to capture the user's microphone in chunks (e.g., base64 encoded PCM data).

- **The Socket:** Opens a secure WebSocket to ws.api.reform.com/chat.

- **Tri-Modal Sending:** If the user speaks, it sends binary audio over the socket. If they click a generated button, it sends a JSON payload: {"event": "click", "value": "3 Stars"}.

### **The Spring Boot Backend (The Orchestrator)**

- **Spring WebSockets (****spring-boot-starter-websocket****):** Configured to accept high-frequency binary and text messages.

- **The Gemini Live API Pipeline:** * Spring Boot acts as the secure middleman. It opens a secondary WebSocket to Google's Gemini Live API.

- As audio chunks arrive from Next.js, Spring Boot pipes them directly to Google.

- Google Gemini processes the audio natively and streams back two things simultaneously: **1. Raw Audio output** (The AI's voice) and **2. A JSON Tool Call** (The instructions to render a multiple-choice button).

- Spring Boot pipes both back to Next.js. Next.js plays the audio buffer and renders the React component instantly.

## **Part 6: Event Storming (The Omni-Modal Micro-Step Loop)**

What exactly happens in the milliseconds after an end-user taps the microphone and says: *"**My screen is cracked**"*?

- 🔵 **Command (Frontend):** Next.js MediaRecorder chunks the user's audio and streams it over the WebSocket to Spring Boot.

- 🟣 **External AI Call (Stream):** Spring Boot pipes the live audio buffer directly into the Gemini 3.1 Live API connection.

- 🟡 **System Decision (Gemini):** Gemini natively processes the audio, realizes the user reported physical damage, and calculates the next step: Ask for a photo.

- 🟠 **Domain Event (Socket Push 1 - Audio):** Gemini streams back the audio buffer for: *"**Oh no! Could you upload a picture of the crack?**"* Spring Boot pipes this to Next.js, which plays it via the Web Audio API.

- 🟠 **Domain Event (Socket Push 2 - UI):** Simultaneously, Gemini outputs a JSON schema: {"ui": "file_dropzone", "label": "Upload Photo"}. Next.js receives this and animates the Dropzone onto the screen.

- 🟠 **Domain Event (DB):** Spring Boot asynchronously uploads the user's audio chunk to AWS S3, gets the URL, and appends the entire interaction to the Postgres conversations JSONB array.

## **Part 7: The ****"****10-Year Architect****"**** Fatal Mistakes**

If you fail building this complex architecture, it will be because of one of these three blunders. Avoid them at all costs.

### **💀 Fatal Mistake #1: The JVM Audio Memory Leak**

- **The Mistake:** You receive the user's audio buffer over the WebSocket and hold it in a standard Java byte[] array in memory until the conversation is over, *then* you try to save it to S3.

- **The Impact:** If a user talks for 5 minutes, that's megabytes of RAM. If 5,000 users are talking, your Spring Boot JVM runs out of heap space and crashes violently with an OutOfMemoryError, dropping every active conversation.

- **The Fix:** Treat audio purely as a passthrough stream. Pipe the incoming WebSocket bytes directly into an InputStream that uploads to S3 via multipart upload in real-time, never holding the full file in JVM memory.

### **💀 Fatal Mistake #2: Waiting for the Audio to Finish**

- **The Mistake:** Your Next.js frontend waits for the AI's audio file to finish downloading and playing before it renders the UI buttons on the screen.

- **The Impact:** The AI says *"**Please select an option**"*, but the user stares at a blank screen for 3 seconds while the system waits for the audio clip to end. It ruins the illusion of a lightning-fast, reactive AI.

- **The Fix:** Ensure your WebSocket message handlers on the frontend decouple Audio events from UI events. The moment the JSON payload for the UI arrives, render it instantly using Framer Motion, even if the AI is only halfway through speaking the question.

### **💀 Fatal Mistake #3: Standard REST for the Live AI**

- **The Mistake:** You ignore the WebSockets architecture and try to implement the Tri-Modal input using standard Spring @PostMapping endpoints.

- **The Impact:** Every time the user speaks a sentence, you have to establish a new HTTP connection, handshake, authenticate, and send the entire chat history back to the server. The overhead introduces a 3-second delay between every single turn.

- **The Fix:** Stick strictly to the persistent WebSocket architecture defined in Part 5 for the OmniModalEngine. (Save the REST endpoints exclusively for your Enterprise API Hub where corporate developers are pulling historical JSON data).

Phase 2

# **⚛️ Phase 2: React Frontend Architecture ****&**** UI/UX Master Guide**

**Project:** Reform (Omni-Modal AI Form Builder)

**Version:** 2.1 (Voice-Visual Sync Engine)

**Role:** Lead Frontend Engineer Blueprint

## **Part 1: Deep Analysis of Your Phase 2 Workflow (Reform v2.1)**

Here is the CTO-level breakdown of exactly what your Next.js architecture must handle to support a talking AI that simultaneously renders UI components.

**1. Wireframing ****&**** Component Architecture (Atomic Design for Omni-Modal)**

- **The Concept:** You build isolated, reusable puzzle pieces and stack them together.

- **The Analysis for Reform:** You now have *three* distinct experiences: The Visual Builder, the Conversational Builder (AI Voice Overlay), and the Omni-Modal End-User Form.

- *Atoms:* BaseInput, VoiceMicButton, AudioVisualizerBar.

- *Molecules:* TriModalDock (Mic + Keyboard + Click), DynamicWidgetWrapper (Fades in UI generated by Gemini).

- *Organisms:* ConversationalBuilderOverlay, OmniModalInterviewInterface.

**2. State Management Decision (The Voice/Visual Matrix)**

- **The Concept:** Managing audio buffers, connection states, and dynamic form schemas simultaneously.

- **The Analysis:** Zustand is mandatory here. You need a useOmniSessionStore. When Gemini sends a WebSocket message containing an audio buffer and a JSON schema, Zustand must independently route the audio to the Web Audio API (so it plays instantly) and update the UI state array (so React renders the buttons) without causing the entire application to re-render.

**3. Server State vs. Socket State**

- **The Concept:** How the frontend talks to the backend.

- **The Analysis:** Standard CRUD (Saving a form, fetching analytics, API key generation) uses **React Query** talking to your REST APIs. The actual AI Conversation uses a **Persistent WebSocket**. React Query cannot handle bi-directional audio streams; a custom robust WebSocket hook is required.

**4. Frontend Optimization Concepts (The Audio-Visual Sync Trap)**

- **The Concept:** React is a UI library, not an audio engine.

- **The Analysis:** If you try to store incoming binary audio chunks in a React useState, you will trigger thousands of re-renders per second, instantly crashing the browser. You must strictly decouple the Audio context (vanilla JS Web Audio API) from the React Component Lifecycle.

## **Part 2: Exhaustive Table of UI/UX Architecture ****&**** Inspiration**

| **UI/UX Concept** | **Explanation** | **Real-World Application for Reform v2.1** |
| --- | --- | --- |
| **Component Libraries** | Do not build complex animations from scratch. | Use **Framer Motion** for the Conversational Builder (blurring the background, smoothly expanding the FileDropzone out of thin air). Use **shadcn/ui** for the dashboard API Hub. |
| **Inspiration Sources** | Steal layouts from proven winners. | Copy **ChatGPT Voice Mode** or **Pi.ai** for the fluid AI audio-visualizer and pulsing microphone UI. Copy **Typeform** for the static blocks. |
| **The ****"****Dynamic Viewport****"** | Mobile browsers have shifting URL bars. | Never use 100vh for the Omni-Modal chat. Use 100dvh (Dynamic Viewport Height) so the TriModalDock stays perfectly anchored above the Safari keyboard. |
| **Microphone Permissions UX** | Users block microphones if asked poorly. | Never ask for microphone permissions on page load. Show a beautifully designed "Start Interview" or "Tap to Speak" intro screen that explains *why* you need the mic before triggering the browser prompt. |

## **Part 3: Exhaustive Table of React Hooks**

| **Hook** | **Primary Purpose** | **Reform Real-World Example** |
| --- | --- | --- |
| useState | Local component state. | Toggling the "Mute AI Voice" button. |
| useEffect | Syncing with external systems. | Opening the WebSocket connection to Spring Boot and initializing the MediaRecorder API when the interview starts. |
| useRef | Mutable variables & DOM access. | Holding the raw audio SourceBuffer so the audio plays without triggering React re-renders. |
| useTransition | Non-blocking state updates. | Keeping the UI responsive when Gemini generates a massive multiple-choice grid that React needs to render. |

### **Detailed Hook Example: The Omni-Modal Socket (****useRef**** + ****useEffect****)**

JavaScript

const audioContextRef = useRef(null);

const webSocketRef = useRef(null);

useEffect(() => {

  // 1. Initialize vanilla JS Audio Context (Kept outside React State!)

  audioContextRef.current = new (window.AudioContext || window.webkitAudioContext)();

  

  // 2. Open WebSocket

  webSocketRef.current = new WebSocket('wss://ws.api.reform.com/interview/123');

  

  webSocketRef.current.onmessage = (event) => {

    const data = JSON.parse(event.data);

    

    // 3A. If Audio: Decode and play instantly (No React re-renders!)

    if (data.audio_base64) {

      playAudioBuffer(audioContextRef.current, data.audio_base64);

    }

    

    // 3B. If UI Instruction: Update Zustand so React renders the buttons

    if (data.generated_ui) {

      omniStore.addDynamicWidget(data.generated_ui);

    }

  };

  return () => webSocketRef.current.close();

}, []);

## **Part 4: State Management Strategy (Zustand)**

Used to manage the highly complex, dynamically generated UI components pushed by the AI.

JavaScript

import { create } from 'zustand';

const useOmniStore = create((set) => ({

  transcript: [],      // The text history

  activeWidget: null,  // The interactive UI currently on screen

  isAiSpeaking: false,

  

  // Appends text AND a UI widget (like a File Upload zone or Rating Scale)

  handleAiTurn: (text, widgetSchema) => set((state) => ({ 

    transcript: [...state.transcript, { role: 'ai', content: text }],

    activeWidget: widgetSchema, 

    isAiSpeaking: true 

  })),

  // Called when user clicks a generated button, speaks, or types

  submitTriModalAnswer: (answerData) => set((state) => ({

    transcript: [...state.transcript, { role: 'user', content: answerData }],

    activeWidget: null, // Clear the screen for the next question

    isAiSpeaking: false

  }))

}));

## **Part 5: The Lead Engineer's Dos, Don'ts, and Fatal Mistakes**

**💀 Fatal Mistake #1: The Audio Memory Leak**

- **The Mistake:** Storing the incoming WebSocket audio buffers inside a React state array (setAudioChunks(prev => [...prev, newChunk])).

- **The Impact:** React will freeze, the browser memory will balloon to 2GB, and the tab will crash within 3 minutes of conversation.

- **The Fix:** Audio must be handled purely by vanilla JavaScript AudioContext and useRef. React should only handle the visual UI (the buttons and text).

**💀 Fatal Mistake #2: Forcing Sequential UX**

- **The Mistake:** You wait for the AI to finish speaking completely before rendering the multiple-choice buttons on the screen.

- **The Impact:** It feels incredibly slow. The user knows the answer immediately but has to wait 4 seconds for the audio to finish before the buttons appear.

- **The Fix:** Synchronized streaming. The moment the WebSocket delivers the JSON UI schema, fade the buttons onto the screen using Framer Motion, even if the AI's audio is still playing.

**💀 Fatal Mistake #3: Ignoring the ****"****Keyboard Push****"**** on Mobile**

- **The Mistake:** Fixing the TriModalDock to the bottom using standard CSS.

- **The Impact:** When the user taps the text input, the iOS/Android virtual keyboard pops up and covers the entire Chat UI and the generated buttons.

- **The Fix:** Use interactive-widget: overlays-content in your meta viewport, and h-[100dvh] so the Tri-Modal Dock shrinks and pushes the UI *above* the keyboard.

# **Part 8: The Next.js i18n Folder Structure ****&**** Data-Driven Component Architecture**

To support the Omni-Modal capabilities and Enterprise APIs while maintaining internationalization (next-intl), we use a highly modular structure.

### **1. The Exhaustive Directory Tree**

Plaintext

reform-web/

├── src/

│   ├── app/

│   │   ├── [locale]/                      

│   │   │   ├── (marketing)/page.tsx       // Marketing Site

│   │   │   ├── (dashboard)/               // Client App

│   │   │   │   ├── workspace/[formId]/page.tsx  // Dual Form Builder

│   │   │   │   ├── analytics/[formId]/page.tsx  // Dashboards & Playback

│   │   │   │   ├── api-hub/page.tsx       // Enterprise API Management

│   │   │   ├── (public)/f/[formId]/page.tsx // The Omni-Modal End-User Form

│   ├── components/                        

│   │   ├── ui/                            // Shadcn Atoms

│   │   ├── shared/                        // 🧬 DATA-DRIVEN REUSABLES

│   │   │   ├── audio/                     // WebRTC & Audio Context components

│   │   │   │   ├── AudioVisualizer.tsx

│   │   │   │   ├── TriModalDock.tsx       // Mic, Text, Click inputs

│   │   │   ├── dynamic-widgets/           // Components generated by AI JSON

│   │   │   │   ├── WidgetRenderer.tsx     // Maps JSON to actual React components

│   │   │   │   ├── AiFileDropzone.tsx

│   │   │   │   ├── AiMultipleChoice.tsx

│   │   │   ├── GenericDataTable.tsx       

│   │   ├── builder/                       

│   │   │   ├── ConversationalOverlay.tsx  // The blurred AI builder UI

│   │   ├── form-engine/                   

│   │   │   ├── OmniModalInterface.tsx     // The main End-User interviewer UI

│   ├── lib/                               

│   │   ├── hooks/useOmniSocket.ts         // The WebSocket Audio/JSON engine

│   │   ├── store/useOmniStore.ts          // Zustand

│   ├── messages/                          // 🌐 i18n Translations (en.json, vi.json)

### **2. Component Breakdown by Page (The React Architect's View)**

Here is exactly how the UI is chopped up into reusable pieces to handle Voice, Visuals, and APIs.

**Page 3: Client Dashboard ****&**** API Hub**

*Location: **components/dashboard/*

- <MetricsRow />: Wraps shared <MetricCard /> (e.g., "Total Voice Minutes Used").

- **API Hub View (****/api-hub****):**

- <WebhookManager />: A dynamic form to add/test on_form_complete endpoint URLs.

- <ApiKeyTable />: Data table for clients to generate keys for Submission Retrieval APIs.

**Page 4: Form Builder (The Dual-Engine)**

*Location: **components/builder/*

- <VisualCanvas />: The standard drag-and-drop workspace.

- <ConversationalOverlay />: **[NEW]** The immersive AI Builder.

- Blurs the background using backdrop-filter.

- <AudioVisualizer variant="large" />: Pulses as Gemini 3.1 speaks.

- <WidgetRenderer />: When the client says "I have an image," this component parses the AI's WebSocket instruction and instantly renders <AiFileDropzone /> in the center of the screen.

**Page 5: Analytics ****&**** Results Dashboard (The Playback Engine)**

*Location: **components/analytics/*

- <AiInsightsTab />: Renders Gemini 3.1 Pro's thematic clustering tables.

- **Individual Responses Tab:**

- <OmniPlaybackViewer />: **[NEW]** A split screen. Shows the text transcript, but includes a <PlaybackTimeline /> so the client can click "Play" and actually hear the end-user's spoken answers and the AI's spoken questions in real-time.

**Page 7: End-User Form View (The Omni-Modal Interviewer)**

*Location: **components/form-engine/*

- <OmniModalInterface />: The 100dvh container.

- **The Output Stream:**

- <AiAvatarHeader />: Shows status ("Listening", "Thinking", "Speaking").

- <StreamingTranscript />: The text of what the AI is currently saying.

- <WidgetRenderer />: **[Crucial]** This component listens to Zustand. If the AI sends {"ui": "rating"}, it instantly renders the 5-star clickable component below the text.

- **The Input Stream:**

- <TriModalDock />: Fixed at the bottom. Contains:

- <VoiceMicButton />: Push-to-talk or tap-to-stream.

- <ChatTextInput />: For users who prefer typing over speaking.

### **3. Senior Implementation: The Dynamic Widget Renderer**

Here is how a Senior React Dev bridges the gap between the Backend AI logic and Frontend UI rendering. The AI sends JSON over the WebSocket, and this component dynamically translates it into interactive React components.

TypeScript

// src/components/shared/dynamic-widgets/WidgetRenderer.tsx

import { useOmniStore } from '@/lib/store/useOmniStore';

import { AiMultipleChoice, AiFileDropzone, AiRatingScale } from './index';

export default function WidgetRenderer() {

  // Reads the active UI schema from Zustand (pushed by the WebSocket)

  const activeWidget = useOmniStore((state) => state.activeWidget);

  const submitTriModalAnswer = useOmniStore((state) => state.submitTriModalAnswer);

  if (!activeWidget) return null;

  // Render the appropriate React component based on Gemini's JSON instruction

  switch (activeWidget.type) {

    case 'multiple_choice':

      return (

        <AiMultipleChoice 

           options={activeWidget.options} 

           // If the user clicks, it submits the answer just like a voice command

           onSelect={(val) => submitTriModalAnswer({ type: 'click', value: val })} 

        />

      );

    

    case 'file_upload':

      return (

        <AiFileDropzone 

           acceptedTypes={activeWidget.allowed_formats}

           onUploadComplete={(url) => submitTriModalAnswer({ type: 'file', value: url })}

        />

      );

    case 'rating_scale':

      return (

        <AiRatingScale 

           max={activeWidget.max_value}

           onSelect={(val) => submitTriModalAnswer({ type: 'click', value: val })}

        />

      );

    default:

      return null;

  }

}

This single component is the magic behind the **Synchronized Voice-Visual Engine**. Gemini does the thinking, sends a lightweight JSON instruction, and your React code instantly provides the user with beautiful, clickable native UI elements while the AI continues to speak.

Phase 3

# **Phase 3: High-Level System Architecture Master Guide**

**Project:** Reform (Omni-Modal AI Form Builder & API Engine)

**Version:** 2.1 (Voice-Visual Sync & Enterprise APIs)

**Role:** Lead Cloud Architect Blueprint

## **Part 1: Deep Analysis of Your Phase 3 Workflow (Reform v2.1)**

To support real-time audio WebSockets, dynamic AI rendering, and Enterprise API consumers, we are making five strict architectural decisions.

**1. The Hybrid Architecture (Modular Monolith + Serverless EDA)**

- **The Concept:** The core ApiEngine and OmniModalEngine are built as a **Modular Monolith** in Spring Boot. However, the Webhook dispatching system uses an **Event-Driven Architecture (EDA)** with Serverless workers.

- **The Analysis:** WebSockets require persistent, long-running servers (Spring Boot on AWS ECS/Fargate). However, pushing 10,000 webhooks to client CRMs (Salesforce, Slack) involves unpredictable network delays. By pushing "Webhook Events" to an event bus, Serverless functions (AWS Lambda) can handle the unreliable outward HTTP calls without blocking your core AI servers.

**2. API Gateway Pattern (Spring Cloud Gateway / AWS API Gateway)**

- **The Concept:** No client—whether a browser, a mobile phone, or an Enterprise developer—talks directly to your Spring Boot backend. They hit api.reform.com.

- **The Analysis:** This is the "Bouncer." For your **Enterprise API Hub**, the Gateway intercepts the request, validates the API key, enforces rate limits (Bucket4j), and routes it. It also terminates SSL, offloading decryption overhead so your Spring Boot servers can dedicate 100% of their CPU to processing audio buffers and JSON payloads.

**3. Layer 4 (TCP) Load Balancing for WebSockets**

- **The Concept:** Standard web apps use Layer 7 (HTTP) Load Balancers. Reform must use a **Layer 4 (TCP) Network Load Balancer (NLB)** for the OmniModalEngine.

- **The Analysis:** L7 load balancers often silently kill idle HTTP connections after 60 seconds. If an end-user takes 65 seconds to think before answering an AI question, an L7 balancer will drop their WebSocket, breaking the audio stream. An L4 NLB holds the raw TCP pipe open persistently, ensuring flawless voice-visual streaming.

**4. Asynchronous Message Brokers (RabbitMQ)**

- **The Concept:** When an Omni-Modal form session ends, Reform must do three things: Save the audio to S3, run Gemini 3.1 Pro analytics, and fire Webhooks.

- **The Analysis:** If the user waits for all three to finish, the loading screen will hang for 20 seconds. We use **RabbitMQ** as a Task Queue. Spring Boot instantly saves the JSONB transcript, drops a FormCompletedEvent into RabbitMQ, and tells the user "Success!" Background workers then read the queue to process the heavy analytics and dispatch the webhooks.

**5. Circuit Breaker Pattern (Resilience4j)**

- **The Concept:** Wrapping external API calls to Google Gemini 3.1 Live API.

- **The Analysis:** If Google Cloud goes down, or Gemini's API experiences a 10-second latency spike, your Spring Boot WebSockets will hang, exhausting server threads. A Circuit Breaker detects the failure, instantly "trips", and returns a polite fallback message to the user (*"**I'm having trouble hearing you, let's switch to text**"*), keeping your servers alive.

## **Part 2: Exhaustive Table of Architectural Styles (The Blueprints)**

How the macro-level codebase and deployment of Reform are organized.

| **Architecture Type** | **Explanation** | **Application for Reform v2.1** |
| --- | --- | --- |
| **Modular Monolith** | Single executable, strictly partitioned code boundaries. | **Core Choice:** WorkspaceManagement, FormCore, and OmniModalEngine live here. Allows you to run the heavy WebSocket logic without complex Kubernetes networks. |
| **Microservices** | Independent apps owning their own databases. | **Avoid for now:** Too complex for Day 1. However, the Enterprise API Hub can easily be extracted into a Microservice later if API traffic overtakes end-user traffic. |
| **Event-Driven Architecture (EDA)** | Services broadcast "Events" to a central bus. | **Crucial for Background Jobs:** When a form completes, FormCore broadcasts an event. The Analytics Engine and Webhook Engine react asynchronously. |
| **Serverless (FaaS)** | Code runs as single, dynamically scaled functions. | **Webhooks:** Used exclusively for dispatching client Webhooks. Automatically scales from 0 to 10,000 concurrent outbound requests without touching your core servers. |
| **Space-Based (In-Memory)** | State is held in distributed RAM. | **Avoid:** Not needed. Postgres JSONB handles our dynamic write speeds perfectly. |
| **Peer-to-Peer (P2P)** | No central server. | **N/A:** Reform requires centralized AI API keys and secure data storage. |

## **Part 3: Exhaustive Table of System Infrastructure Components**

The physical/cloud infrastructure required to host Reform on AWS or Google Cloud.

| **Component** | **Explanation** | **Reform Real-World Usage** | **Why It Matters** |
| --- | --- | --- | --- |
| **API Gateway** | The single front door for HTTP/WS traffic. | api.reform.com. Handles API key validation for the *Submission Retrieval APIs*. | Secures backend IP addresses from direct DDoS attacks. |
| **L4 Network Load Balancer** | Distributes raw TCP traffic. | Placed in front of the OmniModalEngine Spring Boot cluster. | Essential for keeping persistent Voice/WebSocket connections alive without timeout drops. |
| **Message Broker** | Queues tasks (RabbitMQ). | Routes background jobs: GenerateAnalytics, SendWebhook_Salesforce. | Decouples fast end-user actions from slow AI/External API tasks. |
| **Distributed Cache** | High-speed, in-memory store (Redis). | 1. Powers the Token Bucket Rate Limiter. 2. Caches FormSchema for ultra-fast load times. | Protects you from a malicious client spamming their API key to drain your DB. |
| **Content Delivery Network (CDN)** | Global edge servers (CloudFront/Vercel). | Serves the Next.js frontend and static assets. | Ensures form.reform.com loads in <100ms whether the user is in Tokyo or New York. |
| **Web Application Firewall (WAF)** | Security shield against SQLi, XSS, Bots. | Protects the public endpoints from malicious payloads before they hit Spring Boot. | Essential since end-users can upload files/images via the Conversational UI. Blocks malware natively. |
| **Object Storage** | Infinitely scalable file storage (AWS S3). | Stores the raw .wav audio buffers and user-uploaded damage photos. | JVM Memory is expensive; S3 is cheap. Keeps binary data out of your Postgres DB. |

## **Part 4: Exhaustive Table of Cloud ****&**** Distributed Design Patterns**

Patterns utilized to solve the chaos of distributed APIs, Voice, and Webhooks.

| **Design Pattern** | **Explanation** | **Reform Real-World Example** | **Why It Matters** |
| --- | --- | --- | --- |
| **Backend for Frontend (BFF)** | Separate API Gateways tailored for specific clients. | The **Headless Form Rendering API**. An enterprise client requests a simplified JSON schema to build their own UI, hiding Reform's backend complexity. | Gives enterprise clients maximum control without forcing them to parse your internal database models. |
| **Circuit Breaker** | Wraps external API calls to prevent cascading failures. | Wrapping the gemini-3.1-flash-live WebSocket. If Google fails, trip the breaker. | A Google Cloud outage shouldn't crash your entire server cluster. |
| **Retry Pattern** | Auto-retrying failed operations with Exponential Backoff. | Firing client **Webhook APIs**. If the client's HubSpot API is down, Reform waits 2 mins, 4 mins, then 8 mins before trying again. | Ensures clients never lose a lead due to temporary network blips. |
| **Outbox Pattern** | Saving a business entity AND an event to the same DB transaction. | When a user submits a form, you save the JSONB transcript *and* an "Outbox Event" in Postgres. A background worker reads the outbox and sends it to RabbitMQ. | Guarantees that if the Database saves the form, the client *will* eventually receive their Webhook (No dropped data). |
| **CQRS** | Separating Write databases from Read databases. | **Future Scope:** The Analytics Engine writes complex AI summaries, while a separate Read-replica DB serves the Client Dashboard instantly. | Prevents heavy AI data aggregation queries from slowing down live form submissions. |
| **Sidecar Pattern** | Deploying helper components attached to the main app container. | Running an OpenTelemetry sidecar next to your Spring Boot app to monitor exactly how many milliseconds the Gemini audio stream takes. | Keeps your Java code clean of messy infrastructure tracking logic. |
| **Database Sharding** | Horizontally partitioning databases. | **Avoid for V1:** Postgres JSONB on a massive SSD can handle millions of rows. Scale vertically first. | Adds immense complexity. Do not shard until hitting physical limits. |

## **Part 5: The 10-Year Cloud Architect's Dos, Don'ts, and Fatal Mistakes**

Here is where Phase 3 destroys ambitious AI architectures. If you ignore these, Reform will fail in production.

**💀 Fatal Mistake #1: The L7 WebSocket Termination**

- **The Mistake:** You put a standard AWS Application Load Balancer (ALB) or Nginx reverse proxy in front of your Spring Boot servers, configured with default HTTP settings.

- **The Impact:** The end-user is talking to the AI. They pause to think for 45 seconds. The L7 Load Balancer assumes the HTTP connection is "idle" and aggressively closes the WebSocket. The AI abruptly hangs up on the user.

- **The Fix:** You *must* configure an AWS Network Load Balancer (NLB) operating at Layer 4, and explicitly configure WebSocket keep-alive ping/pong frames in both Next.js and Spring Boot to ensure the connection stays hot infinitely.

**💀 Fatal Mistake #2: Unreliable Webhook Dispatching (The Dual-Write Bug)**

- **The Mistake:** Your Spring Boot code says: saveToPostgres(data); sendWebhookToClient(data);.

- **The Impact:** The data saves to Postgres perfectly. Then, your server tries to send the Webhook to the client's Salesforce API, but Salesforce is down. Your Java method throws an Exception, the webhook is lost forever, and the client furiously emails you because they missed a $10,000 lead.

- **The Fix:** Strictly enforce the **Outbox Pattern**. Save the webhook payload into an outbox Postgres table in the *exact same transaction* as the form submission. A separate reliable worker reads the outbox table, uses the **Retry Pattern**, and deletes the row only when Salesforce returns a 200 OK.

**💀 Fatal Mistake #3: Ignoring Enterprise API Rate Limits**

- **The Mistake:** You give an Enterprise client an API key for the "Submission Retrieval API" and let them query it freely.

- **The Impact:** The client's junior developer writes a bad while(true) loop script that queries your API 5,000 times a second. They consume 100% of your database CPU, bringing down the entire app.reform.com dashboard for every other client.

- **The Fix:** Implement **Bucket4j + Redis** at the API Gateway. Assign each API key a strict Token Bucket (e.g., 100 requests per minute). When they hit 101, instantly return HTTP 429 (Too Many Requests) at the edge, protecting your internal DB.

**💀 Fatal Mistake #4: Stateful Audio Servers**

- **The Mistake:** You stream the user's microphone to Server A. Server A stores the audio buffer in its local RAM, waiting to aggregate it.

- **The Impact:** If Server A auto-scales down or crashes, that audio is permanently lost, and the AI conversation breaks midway.

- **The Fix:** The Spring Boot WebSocket servers must be completely **Stateless**. Incoming audio chunks must be piped continuously to Gemini or multi-part uploaded to AWS S3. If a server dies, the client should be able to reconnect to Server B and resume instantly because the JSONB state is safe in Postgres.

Temp

based on this feature list and folder structure, give me a detail plan for 2 teamate, assign task to us, for each week, estimate how long this should be, then give me the plan for the first 1 month

🌟 Part 1: Exhaustive Feature Architecture

1. Form Elements & Question Types (The Building Blocks)

Reform supports a massive array of data collection types. These can be pre-configured by the Builder (Static) or generated on-the-fly by the AI (Dynamic).

Standard Inputs: Short Text (Names, single words), Long Text/Paragraph (Stories, explanations), Email, Phone Number (with country code auto-detect), URL.

Selection Inputs: Multiple Choice (Radio buttons), Checkboxes (Multi-select), Dropdown Menus, Image-Choice (Clickable picture grids).

Quantitative Inputs: Rating Scales (1-5 stars, 1-10 numbers), Opinion Sliders (Sliding scale from "Strongly Disagree" to "Strongly Agree"), NPS (Net Promoter Score).

Complex Inputs: Date/Time Picker, Matrix/Grid (Rate multiple items on the same scale), Signature Pad (Draw to sign).

File & Media Uploads: Standard File Dropzones supporting Images (.png, .jpg), Documents (.pdf, .docx, .txt), and Audio/Video snippets.

The AI Conversational Block (The Brain): A special modular block that hands control over to the Gemini AI Engine to conduct a fluid, dynamic interview based on a core prompt.

2. The Omni-Modal AI Conversational Engine

Tri-Modal Input Sync: End-users can seamlessly switch between speaking into their microphone, typing on their keyboard, or clicking dynamically generated UI widgets on their screen.

Real-Time Live Transcript (Speech-to-Text & Text-to-Speech): As the AI speaks, its words are typed out on the screen in a chat-bubble UI. As the user speaks, their words are instantly transcribed and displayed.

Dynamic UI Generation: The AI does not just output text. If the AI determines a multiple-choice question is best, it streams a JSON schema to the frontend, causing [Option A] and [Option B] buttons to elegantly fade onto the screen below the chat transcript.

Contextual File Prompts (Dynamic Dropzones): If the AI determines it needs a document (e.g., "Could you provide a copy of your ID?"), it triggers a UI event. A sleek File Upload pop-up or inline dropzone dynamically materializes on the screen.

Live Document/Vision Analysis (Gemini Vision): Once a user drops a PDF or picture into the chat, the AI reads it instantly and continues the conversation based on the file's contents.

Multi-Language Auto-Detect: The AI detects the spoken/typed language of the user, replies audibly in that language, and translates all on-screen UI buttons instantly.

3. Authentication, Roles, & Workspace Management

Multi-Provider Auth: JWT-based login via Email/Password, Google OAuth2, and Facebook OAuth2.

Role-Based Access Control (RBAC): ADMIN (Billing/Settings), CREATOR (Builds forms), VIEWER (Can only see analytics).

Workspace Silos: Agencies can create infinite Workspaces (e.g., "Client A", "Client B"). Data, forms, and webhooks are strictly isolated per workspace.

4. Billing, Credits & Enterprise Monetization

Stripe Subscription Tiers: e.g., Free (Basic forms), Pro (AI Voice features), Enterprise (Headless API access).

Credit Ledger System: AI interactions cost credits. Users buy credit packages. Real-time balance checks prevent abuse.

Credit Consumption Dashboard: Visual breakdown of which forms/workspaces are burning the most AI credits.

5. Form Publishing & Distribution

Hosted Public URLs: One-click links (reform.app/f/my-form).

Custom Domains (CNAME): Map forms to survey.client-website.com.

Iframe & Popup Embedding: Copy-paste HTML snippets to embed the form natively on Webflow, WordPress, Shopify, or trigger it as an exit-intent popup.

Headless JSON API: Enterprise feature. Clients fetch the raw AI branching logic via API and render the visual form on their own entirely custom frontend framework.

6. Analytics & Data Actionability

Raw Submissions Data Table: Traditional grid view of all collected data.

Playback Transcript Viewer: Re-read the entire AI conversation, and click "Play" to listen to the raw audio recording of the user.

AI Auto-Summarization: A button that condenses a 20-minute chat transcript into 3 actionable bullet points.

Sentiment & Thematic Tagging: AI automatically tags submissions as "Positive", "Urgent", or "Frustrated", allowing creators to filter thousands of responses instantly.

Data Visualization: Auto-generated pie charts and bar graphs for all quantitative/selection inputs.

7. Enterprise API & Integrations

Event-Driven Webhooks: Fire JSON payloads to custom URLs instantly upon form completion.

Native Integrations: One-click OAuth connections to HubSpot, Salesforce, Slack, and Google Sheets.

API Key Management: Generate secure keys to pull submission data programmatically into internal company dashboards.

🔄 Part 2: In-Depth Workflows

Workflow A: The Form Builder (Your Client/Creator)

Phase 1: Setup & Conception

Login: John logs in via Google Auth. He accesses his "HR Recruitment" Workspace.

Creation Choice: He clicks "New Form". He chooses a Hybrid Flow (combining standard inputs with an AI interview).

Building the Static Layer: Using the drag-and-drop canvas, he adds a Short Text block for "First Name", an Email block, and a File Upload block for "Resume". These are mandatory static fields.

Phase 2: Configuring the AI Brain

Adding the AI Block: John drags the "Conversational AI Engine" block below the static fields.

Setting the Persona & Prompt: He types instructions into the AI setup box: "You are an expert technical recruiter. Review the resume they uploaded. Ask them 3 technical questions about the programming languages listed on their resume. Keep a professional but encouraging tone."

Model Selection: He switches the model from Gemini Flash to Gemini Pro because he needs the AI to deeply analyze the uploaded resume.

Guardrails & Constraints: He toggles "Enable Dynamic File Requests" to ON, allowing the AI to ask for a portfolio link or PDF if the user mentions one. He sets a hard limit: "Max 5 questions total."

Phase 3: Publishing & Analysis

Publishing: John clicks "Publish", generates an embed code, and pastes it into his company's careers page.

Webhook Setup: He configures a webhook: "When AI tags sentiment as 'Highly Qualified', push data to Slack channel #hiring."

Reviewing: Days later, John logs in. He views the Dashboard. He clicks on a specific candidate's transcript, reads the dynamically generated questions the AI asked, and listens to the candidate's audio responses.

Workflow B: The Form Filler (The End-User / Customer)

Phase 1: The Static Entry

Arrival: Sarah, an applicant, visits the careers page on her laptop. She sees the embedded Reform form.

Standard Input: She types her name, her email, and drops her PDF resume into the standard static File Dropzone. She clicks "Begin Interview".

Phase 2: The AI Takeover (The Omni-Modal Experience)

Visual Transition: The screen seamlessly transitions. The background slightly blurs, bringing focus to a sleek chat interface in the center of the screen. A gentle audio chime plays.

The AI Speaks: From her computer speakers, a warm, professional voice says: "Hi Sarah, thanks for applying. I see you have 3 years of experience in Java. Could you tell me about the most challenging Java project you've worked on?"

Live Transcript Rendering: As the AI speaks, its words smoothly type out on the screen in an AI-chat bubble.

User Response (Tri-Modal): Sarah can type her answer, but she prefers to speak. She holds the on-screen mic button (or spacebar) and says: "I built a modular monolith architecture for an e-commerce platform." As she speaks, her words are transcribed instantly onto the screen in a User-chat bubble.

Phase 3: Dynamic UI & Contextual Intelligence

Dynamic Options Fade In: The AI analyzes her audio instantly. The AI speaks: "A modular monolith is a great choice. Why didn't you choose Microservices right away?"

Simultaneously, the AI streams JSON instructions to the frontend. Below the chat text, three clickable UI buttons smoothly fade in: [Cost/Overhead], [Team Size], [Time to Market], along with an [Other] text box.

Clicking vs Speaking: Sarah doesn't want to speak this time. She simply clicks the [Cost/Overhead] button on her screen. The system accepts this instantly.

Dynamic File Request: The AI processes this and replies: "Makes complete sense. You mentioned earlier you designed the architecture. Do you happen to have a PDF of the architecture diagram you could share?"

The Dynamic Dropzone: As the AI asks this, a visually distinct "Upload Architecture Diagram" modal dynamically slides up on her screen, featuring a drag-and-drop zone.

Multimodal Analysis: Sarah drags a PDF into the dropzone. A quick loading animation spins. Gemini Vision reads the diagram. The AI speaks: "I see you used Spring Boot and PostgreSQL. Very solid setup. We use the same stack here."

Phase 4: Wrap Up & Finalization

Conclusion: The AI reaches the 5-question limit set by the Builder. The AI says: "This has been fantastic, Sarah. Our team will review this and get back to you by Friday. Have a great day!"

Completion State: The chat interface fades out into a beautifully animated "Success / Thank You" screen.

Background Action: The connection securely closes. The full JSON tree of the conversation, the raw audio files, and the extracted data are saved to the PostgreSQL database, and the Webhook fires to the HR team's Slack.

This is the ultra-comprehensive, production-grade architecture for Reform.

This structure is designed for High Availability (HA), Horizontal Scalability, and Enterprise Security. It implements a Domain-Driven Modular Monolith that is ready to be broken into microservices if you ever reach millions of users, but is optimized for "thousands of users" using Redis for distributed state and Load Balancing logic.

📁 Reform Backend: Full Structural Blueprint

codeText

src/main/java/com/reform/app

├── 📁 core                          # SHARED KERNEL (Cross-cutting concerns)

│   ├── 📁 config                    # Global Infrastructure Configuration

│   │   ├── 📄 AsyncConfig.java             # Executor service for background AI/Webhook tasks

│   │   ├── 📄 WebSocketConfig.java         # Bi-directional stream config (STOMP/Raw WS)

│   │   ├── 📄 RedisConfig.java             # NEW: Distributed Cache & Pub/Sub configuration

│   │   ├── 📄 OpenApiConfig.java           # NEW: Swagger/OpenAPI for Enterprise API Docs

│   │   ├── 📄 JacksonConfig.java           # Poly-morphic JSON handling for Dynamic UI blocks

│   │   └── 📄 JpaConfig.java               # DB Auditing & Persistence tuning

│   ├── 📁 domain                    # Base DDD constructs

│   │   ├── 📄 BaseEntity.java              # UUIDs, Versioning (Optimistic Locking), Timestamps

│   │   └── 📄 JsonbConverter.java          # Postgres JSONB <-> Jackson JsonNode mapper

│   ├── 📁 exception                 # Centralized Error System

│   │   ├── 📄 GlobalExceptionHandler.java  # Maps exceptions to RFC-7807 Error Details

│   │   ├── 📄 RateLimitException.java      # NEW: 429 Too Many Requests handler

│   │   └── 📄 InsufficientCreditsException.java

│   ├── 📁 interceptor               # NEW: Request-level orchestration

│   │   └── 📄 RateLimitInterceptor.java    # NEW: Redis-backed Bucket4j rate limiter

│   ├── 📁 util                      # Static helpers

│   │   ├── 📄 SecurityUtils.java           # Extract UserID/WorkspaceID from SecurityContext

│   │   └── 📄 AudioFormatUtils.java        # Helpers for processing Web Audio API buffers

│   └── 📁 event                     # Internal Observer Pattern

│       └── 📄 DomainEventPublisher.java    # Spring ApplicationEventPublisher wrapper

│

├── 📁 auth                          # IDENTITY & ACCESS (Stateless Security)

│   ├── 📁 config                    # Spring Security Filter Chain

│   │   └── 📄 SecurityConfig.java          # JWT, CORS, OAuth2, and URL-level permissions

│   ├── 📁 filter                    

│   │   ├── 📄 JwtAuthenticationFilter.java # Validates Bearer tokens per request

│   │   └── 📄 ApiKeyAuthFilter.java        # Validates Enterprise X-API-KEY headers

│   ├── 📁 security                  # Logic implementations

│   │   ├── 📄 JwtProvider.java             # Token generation/rotation logic

│   │   ├── 📄 CustomUserDetailsService.java# Load user for Spring Security context

│   │   └── 📄 OAuth2SuccessHandler.java    # Social login callback -> JWT generation

│   ├── 📁 controller                

│   │   └── 📄 AuthController.java          # Register, Login, Social Link, MFA, Password Reset

│   └── 📁 dto                       

│       ├── 📄 AuthResponseDTO.java

│       └── 📄 LoginRequestDTO.java

│

├── 📁 user                          # USER & WORKSPACE (The Multi-tenancy Layer)

│   ├── 📁 entity

│   │   ├── 📄 User.java                    # Identity, Preferences, Locale

│   │   ├── 📄 Role.java                    # Enum: ADMIN, CREATOR, VIEWER

│   │   └── 📄 Workspace.java               # The logical container for forms/data

│   ├── 📁 service

│   │   ├── 📄 UserService.java             

│   │   └── 📄 WorkspaceService.java        # Logic for team invites & workspace isolation

│   ├── 📁 mapper                    

│   │   └── 📄 UserMapper.java              # MapStruct: Entity <-> DTO

│   └── 📁 repository

│       └── 📄 UserRepository.java          # Cacheable user lookups

│

├── 📁 billing                       # REVENUE & CREDITS (Stripe & Ledger)

│   ├── 📁 entity

│   │   ├── 📄 CreditLedger.java            # Immutable record of every credit change

│   │   ├── 📄 SubscriptionPlan.java        # FREE, PRO, ENTERPRISE limits

│   │   └── 📄 StripeCustomer.java          # Mapping of Internal User to Stripe ID

│   ├── 📁 service

│   │   ├── 📄 StripeService.java           # SDK integration for Checkout/Billing Portal

│   │   └── 📄 CreditManager.java           # Thread-safe credit deduction logic

│   ├── 📁 controller

│   │   ├── 📄 BillingController.java       # Buy credits, Manage subscription

│   │   └── 📄 StripeWebhookController.java # LISTENS: Critical async payment success events

│   └── 📁 event

│       └── 📄 CreditDeductionEvent.java    # Triggered by AI usage

│

├── 📁 ai                            # THE BRAIN (Gemini Orchestration)

│   ├── 📁 config

│   │   └── 📄 GeminiClientConfig.java      # WebClient config for Vertex AI / Google AI

│   ├── 📁 strategy                  # STRATEGY PATTERN: AI Models

│   │   ├── 📄 IAIEngineStrategy.java       # Model Interface

│   │   ├── 📄 GeminiFlashStrategy.java     # Fast/Voice sync model

│   │   ├── 📄 GeminiProStrategy.java       # Complex reasoning/Analysis model

│   │   └── 📄 GeminiVisionStrategy.java    # Vision/File analysis model

│   ├── 📁 memory                    # NEW: Redis-backed context

│   │   └── 📄 ConversationMemoryStore.java # NEW: Saves chat history for Gemini "context"

│   ├── 📁 service

│   │   ├── 📄 AIModelFactory.java          # Selects strategy based on task/user-plan

│   │   └── 📄 AIOperatorFacade.java        # Checks Credits -> Calls Model -> Deducts Credits

│   └── 📁 mapper

│       └── 📄 AIResultMapper.java          # Extracts structured JSON/Voice from Gemini output

│

├── 📁 form                          # DESIGNER ENGINE (Structure & Blocks)

│   ├── 📁 entity

│   │   ├── 📄 Form.java                    # Metadata, Theme, CNAME, Workspace ref

│   │   └── 📁 block                        # Question hierarchy

│   │       ├── 📄 FormBlock.java           # Base abstract class

│   │       ├── 📄 StaticBlock.java         # Text, Email, Rating

│   │       └── 📄 ConversationalBlock.java # AI Prompt, Persona, Max Questions

│   ├── 📁 factory

│   │   └── 📄 BlockFactory.java            # Creates specific blocks from Builder requests

│   ├── 📁 validation               

│   │   └── 📄 FormValidator.java           # Business rules: "Only 1 AI block allowed per free form"

│   ├── 📁 service

│   │   └── 📄 FormService.java             # CRUD + Redis Caching for public form lookups

│   └── 📁 controller

│       └── 📄 BuilderController.java       # API for the Next.js Canvas Builder

│

├── 📁 submission                    # EXECUTION ENGINE (The Omni-modal Sync)

│   ├── 📁 entity

│   │   └── 📄 Submission.java              # The final result record (JSONB)

│   ├── 📁 controller

│   │   ├── 📄 ConversationalWSHandler.java # WS: Bi-directional Voice/Visual pipeline

│   │   └── 📄 SubmissionController.java    # REST: Static form fallback

│   ├── 📁 service

│   │   ├── 📄 TranscriptService.java       # Orchestrates live STT/TTS

│   │   └── 📄 SubmissionService.java       # Finalizes chat -> Saves DB -> Publishes Event

│   ├── 📁 state                    # NEW: Distributed Session Management

│   │   └── 📄 SessionTracker.java          # Redis-based: Which server is Sarah talking to?

│   └── 📁 dto

│       └── 📄 OmniModalFrameDTO.java       # Audio payload + Next.js UI Schema

│

├── 📁 storage                       # NEW: MEDIA ASSETS (Cloud Storage)

│   ├── 📁 service

│   │   ├── 📄 StorageService.java          # Interface for S3/Google Cloud Storage

│   │   └── 📄 CloudStorageServiceImpl.java # Logic for Voice clips & Vision uploads

│   └── 📁 dto

│       └── 📄 FileSignedUrlDTO.java        # Secure, time-limited URLs for form files

│

├── 📁 analytics                     # INSIGHTS (Post-Processing)

│   ├── 📁 service

│   │   ├── 📄 SentimentService.java        # Gemini-based sentiment analyzer

│   │   └── 📄 AggregateReportService.java  # Summarizes thousands of transcripts

│   └── 📁 controller

│       └── 📄 AnalyticsController.java     # Data for Creator Dashboard charts

│

└── 📁 enterprise                    # B2B & EXTERNAL (The API Revenue Engine)

    ├── 📁 entity

    │   ├── 📄 ApiKey.java                  # Hashed keys + Metadata

    │   └── 📄 WebhookConfig.java           # Destination URL & Secret

    ├── 📁 controller

    │   ├── 📄 HeadlessAPIController.java   # Returns raw AI routing logic

    │   └── 📄 ExternalIntegrationController.# HubSpot/Salesforce OAuth Handlers

    └── 📁 service

        ├── 📄 WebhookDispatcher.java       # Async: Retries failed webhook pushes

        └── 📄 ExternalSyncService.java     # Pushes data to CRMs

Steps to draw Class Diagram for MVP

To move from a business document to a technical class diagram, you need to think like a translator. You are translating **human requirements** into **objects, properties, and relationships**.

Since you are "learning by doing," follow these **5 specific steps** to design the MVP class diagram.

### **Step 1: Identify the ****"****Nouns****"**** (Entities)**

Read your MVP list and underline every "thing" that needs to be saved in a database or tracked by the system.

- **User:** Someone who logs in.

- **Workspace:** The container where forms live.

- **Form:** The actual survey/interview.

- **FormBlock:** A piece of a form (e.g., "Email field").

- **Submission:** The result when someone fills a form.

- **CreditLedger:** Tracking if they can afford an AI call.

### **Step 2: Define the ****"****Relationships****"**

Ask yourself: *How do these nouns talk to each other?*

- A **User** owns a **Workspace** (1 to 1).

- A **Workspace** has many **Forms** (1 to Many).

- A **Form** contains many **FormBlocks** (Composition).

- A **Form** produces many **Submissions** (1 to Many).

### **Step 3: Add the ****"****Attributes****"**** (What do they know?)**

For each noun, list the data it must hold to satisfy the MVP.

- **Form:** Needs a title, a publicUrl, and isActive.

- **ConversationalBlock:** Needs an aiPrompt and aiPersona.

- **Submission:** Needs a jsonData (the answers) and a transcript.

### **Step 4: Layering (How do they move?)**

In a Spring Boot app, code doesn't just sit in a "Class." It moves through layers.

- **Controller:** The "Entry Door" (receives the request).

- **Service:** The "Brain" (processes logic, e.g., "Check if user has credits").

- **Repository:** The "Librarian" (talks to the Database).

- **DTO:** The "Envelope" (packages data to send to the frontend).

### **Step 5: Apply Design Patterns**

This is where you make the system **Scalable**.

- **Strategy Pattern:** For the AI. Why? Because you'll start with "Free Gemini," but might add "Paid GPT" later.

- **Facade Pattern:** For the AI Orchestration. Why? Because the SubmissionService shouldn't have to know *how* to talk to Google; it just asks the AIOperatorFacade to "get an answer."

Tab 14

Dependency injection

User -> Credit 

Private Credit ddsf

New credit 

constructor 

Main -> new Credit(200) -> Scanner (“thien”)

User(Credit creditService, Scanner){}

setCredit

setCredit

User

Spring boot

Spring IoC Container (inversion of control)

@requiredargconstructor

User

Private ICreditProvider creditProvider; tightly coupled creditProviderA B

Private Scanner

creditProvider.method()

SOLID

methodabcd

CreditProviderA implements ICreditProvider

Controller, Service, Repo, Entity