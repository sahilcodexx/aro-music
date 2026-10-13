"use client";

import { useState } from "react";
import { Icon } from "./icons";

function Question({ question, answer }: { question: string; answer: string }) {
  const [open, setOpen] = useState(false);
  return (
    <details onToggle={(event) => setOpen(event.currentTarget.open)}>
      <summary>
        {question}
        <span
          className="t-icon-swap"
          data-state={open ? "b" : "a"}
          aria-hidden="true"
        >
          <span className="t-icon" data-icon="a">
            <Icon name="plus" />
          </span>
          <span className="t-icon" data-icon="b">
            <Icon name="minus" />
          </span>
        </span>
      </summary>
      <p>{answer}</p>
    </details>
  );
}
export default function Questions({ questions }: { questions: string[][] }) {
  return (
    <div className="faq-list">
      {questions.map(([question, answer]) => (
        <Question key={question} question={question} answer={answer} />
      ))}
    </div>
  );
}
