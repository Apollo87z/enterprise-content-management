import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="panel empty">
      <h1>Page not found</h1>
      <Link to="/">Return to dashboard</Link>
    </div>
  );
}
