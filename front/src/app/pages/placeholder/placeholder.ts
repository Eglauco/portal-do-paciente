import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-placeholder',
  imports: [RouterLink],
  templateUrl: './placeholder.html',
})
export class Placeholder {
  private readonly route = inject(ActivatedRoute);

  protected readonly title = (this.route.snapshot.data['title'] as string) ?? 'Seção';
  protected readonly description =
    (this.route.snapshot.data['description'] as string) ?? 'Conteúdo em construção.';
}
