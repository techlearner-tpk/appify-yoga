export function Loading(){return <div className="statusCard">Loading your space…</div>}
export function ErrorNotice({message}:{message:string}){return <div className="errorCard" role="alert">{message}</div>}
export function Empty({message}:{message:string}){return <div className="statusCard">{message}</div>}
