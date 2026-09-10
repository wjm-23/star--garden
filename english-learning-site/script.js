const wordBank = [
  {
    word: "persistent",
    meaning: "持续的；坚持不懈的",
    example: "She is a persistent learner who studies every day."
  },
  {
    word: "confident",
    meaning: "自信的",
    example: "He feels more confident when he speaks clearly."
  },
  {
    word: "improve",
    meaning: "提升；改进",
    example: "Daily practice can improve your English quickly."
  },
  {
    word: "vocabulary",
    meaning: "词汇表；词汇量",
    example: "A larger vocabulary helps you read faster."
  },
  {
    word: "fluency",
    meaning: "流利程度",
    example: "Listening and speaking together build fluency."
  },
  {
    word: "progress",
    meaning: "进步；发展",
    example: "Small steps lead to real progress."
  }
];

const quizBank = [
  {
    question: "Which sentence is correct?",
    options: [
      "I have went to the library yesterday.",
      "I went to the library yesterday.",
      "I go to the library yesterday.",
      "I was went to the library yesterday."
    ],
    answer: 1
  },
  {
    question: "Choose the best answer: 'She ___ a new book last week.'",
    options: ["buy", "buys", "bought", "buying"],
    answer: 2
  },
  {
    question: "What does 'improve' mean?",
    options: ["to become worse", "to make better", "to stop trying", "to ignore"],
    answer: 1
  }
];

const wordText = document.getElementById("wordText");
const phraseText = document.getElementById("phraseText");
const exampleText = document.getElementById("exampleText");
const shuffleWordBtn = document.getElementById("shuffleWord");

function renderWord() {
  const item = wordBank[Math.floor(Math.random() * wordBank.length)];
  wordText.textContent = item.word;
  phraseText.textContent = item.meaning;
  exampleText.textContent = item.example;
}

shuffleWordBtn.addEventListener("click", renderWord);
renderWord();

const quizQuestion = document.getElementById("quizQuestion");
const quizOptions = document.getElementById("quizOptions");
const quizProgress = document.getElementById("quizProgress");
const nextQuizBtn = document.getElementById("nextQuiz");

let currentQuizIndex = 0;
let answered = false;

function renderQuiz() {
  const quiz = quizBank[currentQuizIndex];
  quizQuestion.textContent = quiz.question;
  quizOptions.innerHTML = "";
  quizProgress.textContent = `${currentQuizIndex + 1} / ${quizBank.length}`;
  answered = false;

  quiz.options.forEach((option, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "quiz-option";
    button.textContent = option;
    button.addEventListener("click", () => {
      if (answered) return;
      answered = true;

      const options = Array.from(quizOptions.children);
      options.forEach((item, idx) => {
        item.disabled = true;
        if (idx === quiz.answer) {
          item.classList.add("correct");
        }
      });

      if (index !== quiz.answer) {
        button.classList.add("wrong");
      }
    });
    quizOptions.appendChild(button);
  });
}

nextQuizBtn.addEventListener("click", () => {
  currentQuizIndex = (currentQuizIndex + 1) % quizBank.length;
  renderQuiz();
});

renderQuiz();
