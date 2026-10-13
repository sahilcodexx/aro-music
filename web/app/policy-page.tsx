import Link from "next/link";
import type { ReactNode } from "react";
import { SiteHeader, SiteFooter } from "./site-shell";
import { Icon } from "./icons";
export type PolicySection = { id: string; title: string; body: ReactNode };
export default function PolicyPage({
  title,
  intro,
  sections,
}: {
  title: string;
  intro: string;
  sections: PolicySection[];
}) {
  return (
    <>
      <SiteHeader />
      <main id="main" className="container policy-page">
        <Link className="text-link policy-back" href="/">
          <Icon name="back" /> Back to Aro
        </Link>
        <div className="policy-heading">
          <h1>{title}</h1>
          <p>{intro}</p>
          <p className="policy-date">Updated October 13, 2026</p>
        </div>
        <div className="policy-grid">
          <aside className="policy-navigation">
            <nav aria-label="On this page">
              <p>On this page</p>
              {sections.map((s) => (
                <a key={s.id} href={`#${s.id}`}>
                  {s.title}
                </a>
              ))}
            </nav>
          </aside>
          <article className="policy-content">
            {sections.map((s) => (
              <section id={s.id} key={s.id}>
                <h2>{s.title}</h2>
                {s.body}
              </section>
            ))}
          </article>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
