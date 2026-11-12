// İSTƏYƏ BAĞLI: boş market üçün nümunə KATEQORİYALAR yaradır (məhsul, qiymət və stok yaratmır –
// real məhsulları admin tətbiqdən əlavə edir).
import { pool } from '../src/db.js';

const cats = [
  ['Süd məhsulları', '🥛', '#1E88E5'], ['Çörək və un', '🍞', '#F9A825'], ['Meyvə və tərəvəz', '🍎', '#43A047'],
  ['Ət və toyuq', '🍗', '#E53935'], ['İçkilər', '🧃', '#8E24AA'], ['Şirniyyat', '🍫', '#6D4C41'],
  ['Ərzaq', '🍚', '#FB8C00'], ['Təmizlik', '🧼', '#00ACC1'],
];
const { rows } = await pool.query('SELECT count(*)::int AS n FROM categories');
if (rows[0].n > 0) { console.log('Kateqoriyalar artıq mövcuddur – heç nə edilmədi.'); }
else {
  for (const [i, [name, icon, color]] of cats.entries()) {
    await pool.query('INSERT INTO categories (name, icon, color, sort_order) VALUES ($1,$2,$3,$4)', [name, icon, color, i]);
  }
  console.log(`${cats.length} kateqoriya yaradıldı.`);
}
await pool.end();
